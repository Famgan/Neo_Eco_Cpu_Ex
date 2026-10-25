#!/usr/bin/env python3
"""Build a transparent 512x512 mod icon from an isometric block screenshot.

Usage:
    python tools/make_icon.py input.png -o src/main/resources/icon.png

Background removal is connectivity-gated: flood fill starts only at the four
image corners and never enters an enclosed light-coloured area such as the
block's top lid.
"""

from __future__ import annotations

import argparse
import math
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


Color = tuple[int, int, int]
Point = tuple[float, float]


def color_distance(a: Color, b: Color) -> float:
    """Euclidean RGB distance used only for matching the corner background."""
    return math.sqrt(sum((int(x) - int(y)) ** 2 for x, y in zip(a, b)))


def corner_background_colors(image: Image.Image) -> list[Color]:
    """Sample the four corners, which are guaranteed background by convention."""
    width, height = image.size
    pixels = image.load()
    corners = ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1))
    return [pixels[x, y][:3] for x, y in corners]


def remove_corner_connected_background(
    image: Image.Image,
    tolerance: int = 8,
) -> Image.Image:
    """Make only corner-connected background pixels transparent.

    A plain colour threshold is deliberately not used. A pixel is removed only
    when it is close to a corner background colour AND reachable from a corner
    by a four-neighbour flood fill. Light pixels enclosed by the block remain
    completely intact. No mask dilation is performed, so the silhouette is not
    eroded.
    """
    rgba = image.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()
    backgrounds = corner_background_colors(rgba)

    background_mask = Image.new("L", (width, height), 0)
    mask_pixels = background_mask.load()

    def matches_background(x: int, y: int) -> bool:
        r, g, b, alpha = pixels[x, y]
        if alpha == 0:
            return True
        colour = (r, g, b)
        return any(color_distance(colour, bg) <= tolerance for bg in backgrounds)

    queue: deque[tuple[int, int]] = deque()
    corner_seeds = ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1))
    for x, y in corner_seeds:
        if mask_pixels[x, y] == 0 and matches_background(x, y):
            mask_pixels[x, y] = 255
            queue.append((x, y))

    if not queue:
        raise ValueError("None of the four corners looks like background")

    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if not (0 <= nx < width and 0 <= ny < height):
                continue
            if mask_pixels[nx, ny] != 0:
                continue
            if matches_background(nx, ny):
                mask_pixels[nx, ny] = 255
                queue.append((nx, ny))

    for y in range(height):
        for x in range(width):
            if mask_pixels[x, y] == 255:
                r, g, b, _ = pixels[x, y]
                pixels[x, y] = (r, g, b, 0)

    return rgba


def centre_on_canvas(image: Image.Image, size: int, margin: float) -> Image.Image:
    """Crop to alpha bounds and fit the block onto a centred transparent square."""
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("No foreground pixels found after background removal")
    block = image.crop(bounds)

    inner = max(1, round(size * (1.0 - 2.0 * margin)))
    scale = min(inner / block.width, inner / block.height)
    new_size = (max(1, round(block.width * scale)), max(1, round(block.height * scale)))
    block = block.resize(new_size, Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(block, ((size - new_size[0]) // 2, (size - new_size[1]) // 2))
    return canvas


def find_dark_face(image: Image.Image, darkness: int = 72) -> tuple[int, int, int, int] | None:
    """Return the bounding box of the largest connected dark component, if useful."""
    width, height = image.size
    pixels = image.load()
    remaining: set[tuple[int, int]] = set()
    for y in range(height):
        for x in range(width):
            r, g, b, alpha = pixels[x, y]
            if alpha > 96 and max(r, g, b) < darkness:
                remaining.add((x, y))

    best: list[tuple[int, int]] | None = None
    while remaining:
        start = remaining.pop()
        queue = [start]
        component = [start]
        while queue:
            x, y = queue.pop()
            for neighbour in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    queue.append(neighbour)
                    component.append(neighbour)
        if best is None or len(component) > len(best):
            best = component

    if not best:
        return None

    xs = [point[0] for point in best]
    ys = [point[1] for point in best]
    bounds = (min(xs), min(ys), max(xs), max(ys))
    box_width = bounds[2] - bounds[0] + 1
    box_height = bounds[3] - bounds[1] + 1
    if box_width < width * 0.08 or box_height < height * 0.08:
        return None
    return bounds


def infinity_points(center: Point, width: float, samples: int = 2400) -> list[Point]:
    """Sample a smooth horizontal lemniscate, taller than the raw Gerono curve."""
    cx, cy = center
    half_width = width / 2.0
    points: list[Point] = []
    for index in range(samples + 1):
        angle = math.tau * index / samples
        cosine = math.cos(angle)
        denominator = 1.0 + math.sin(angle) ** 2
        x = cosine / denominator
        y = math.sin(angle) * cosine / denominator
        # Map the normalized curve to a pleasing icon proportion (height ~= 0.55 width).
        points.append((cx + x * half_width, cy + y * half_width * 1.10))
    return points


def draw_glowing_infinity(
    size: int,
    center: Point,
    width: float,
    supersample: int = 4,
) -> Image.Image:
    """Render a crisp white infinity with violet bloom on a transparent layer."""
    high_size = size * supersample
    points = infinity_points((center[0] * supersample, center[1] * supersample), width * supersample)
    core_width = max(2, round(width * 0.055)) * supersample
    layer = Image.new("RGBA", (high_size, high_size), (0, 0, 0, 0))

    # Wide bloom.
    bloom = Image.new("RGBA", (high_size, high_size), (0, 0, 0, 0))
    bloom_draw = ImageDraw.Draw(bloom)
    bloom_draw.line(points, fill=(143, 41, 255, 120), width=core_width * 5, joint="curve")
    bloom = bloom.filter(ImageFilter.GaussianBlur(radius=core_width * 1.45))
    layer = Image.alpha_composite(layer, bloom)

    # Tighter inner glow.
    inner = Image.new("RGBA", (high_size, high_size), (0, 0, 0, 0))
    inner_draw = ImageDraw.Draw(inner)
    inner_draw.line(points, fill=(214, 106, 255, 205), width=core_width * 3, joint="curve")
    inner = inner.filter(ImageFilter.GaussianBlur(radius=core_width * 0.62))
    layer = Image.alpha_composite(layer, inner)

    # Bright core.
    core = Image.new("RGBA", (high_size, high_size), (0, 0, 0, 0))
    core_draw = ImageDraw.Draw(core)
    core_draw.line(points, fill=(255, 249, 255, 255), width=core_width, joint="curve")
    layer = Image.alpha_composite(layer, core)

    return layer.resize((size, size), Image.Resampling.LANCZOS)


def build_icon(source: Path, output: Path, size: int, margin: float, tolerance: int) -> None:
    original = Image.open(source)
    transparent = remove_corner_connected_background(original, tolerance=tolerance)
    icon = centre_on_canvas(transparent, size, margin)

    face = find_dark_face(icon)
    if face is not None:
        left, top, right, bottom = face
        face_width = right - left + 1
        center = ((left + right) / 2.0, (top + bottom) / 2.0)
        symbol_width = min(size * 0.42, max(size * 0.28, face_width * 0.92))
    else:
        center = (size / 2.0, size / 2.0)
        symbol_width = size * 0.38

    infinity = draw_glowing_infinity(size, center, symbol_width)
    icon = Image.alpha_composite(icon, infinity)

    output.parent.mkdir(parents=True, exist_ok=True)
    icon.save(output, format="PNG", optimize=True)
    print(f"Wrote {output} ({size}x{size}, RGBA)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create the Neo Eco Cpu Ex mod icon")
    parser.add_argument("input", type=Path, help="source isometric block screenshot")
    parser.add_argument("-o", "--output", type=Path, default=Path("icon.png"))
    parser.add_argument("--size", type=int, default=512, help="output square size (default: 512)")
    parser.add_argument(
        "--margin",
        type=float,
        default=0.06,
        help="transparent margin around the block as a fraction of the icon (default: 0.06)",
    )
    parser.add_argument(
        "--tolerance",
        type=int,
        default=8,
        help="RGB tolerance for the corner-connected background fill (default: 8)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    build_icon(args.input, args.output, args.size, args.margin, args.tolerance)