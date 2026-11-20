"""Generate launcher raster resources from the user-approved AI artwork.

This changes icon assets only. Android's adaptive viewport is 108dp with a
72dp masked viewport, so inset the approved full canvas to preserve its scale.
"""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'artifacts/icon-redesign'
RES = ROOT / 'app/src/main/res'
BACKGROUND = '#14172B'
DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def generate():
    foreground = Image.open(ART / 'pixel-money-icon-concept.png').convert('RGBA')
    # Remove only effectively transparent generation noise, not visible artwork.
    alpha = foreground.getchannel('A')
    foreground.putalpha(alpha.point(lambda v: 0 if v < 16 else v))
    approved = Image.new('RGBA', foreground.size, BACKGROUND)
    approved.alpha_composite(foreground)
    for density, size in DENSITIES.items():
        folder = RES / f'mipmap-{density}'
        folder.mkdir(exist_ok=True)
        square = approved.resize((size, size), Image.Resampling.LANCZOS)
        square.convert('RGB').save(folder / 'ic_launcher.png', optimize=True)
        high = approved.resize((size * 4, size * 4), Image.Resampling.LANCZOS)
        mask = Image.new('L', high.size)
        ImageDraw.Draw(mask).ellipse((0, 0, high.width - 1, high.height - 1), fill=255)
        high.putalpha(mask)
        high.resize((size, size), Image.Resampling.LANCZOS).save(folder / 'ic_launcher_round.png', optimize=True)

    # 1536:1024 == 108dp:72dp, matching the approved preview inside masks.
    adaptive = Image.new('RGBA', (1536, 1536))
    scaled = foreground.resize((1024, 1024), Image.Resampling.LANCZOS)
    adaptive.alpha_composite(scaled, (256, 256))
    folder = RES / 'drawable-nodpi'
    folder.mkdir(exist_ok=True)
    adaptive.save(folder / 'ic_launcher_art.png', optimize=True)
    mono = Image.new('RGBA', adaptive.size, 'white')
    # Preserve the dark spine/check as cutouts for Android's themed icon mode.
    ink = adaptive.convert('L').point(lambda value: 255 if value >= 100 else 0)
    mono.putalpha(ImageChops.multiply(adaptive.getchannel('A'), ink))
    mono.save(folder / 'ic_launcher_mono_art.png', optimize=True)

    # Display at actual launcher proportions, with all popular mask forms.
    sheet = Image.new('RGB', (960, 400), '#EBEDF3')
    for index, shape in enumerate(['square', 'circle', 'squircle']):
        tile = approved.resize((240, 240), Image.Resampling.LANCZOS)
        mask = Image.new('L', (240, 240))
        draw = ImageDraw.Draw(mask)
        if shape == 'circle':
            draw.ellipse((0, 0, 239, 239), fill=255)
        elif shape == 'squircle':
            draw.rounded_rectangle((0, 0, 239, 239), radius=58, fill=255)
        else:
            draw.rectangle((0, 0, 239, 239), fill=255)
        sheet.paste(tile.convert('RGB'), (40 + index * 320, 60), mask)
        ImageDraw.Draw(sheet).text((40 + index * 320, 325), shape, fill='#14172B')
    sheet.save(ART / 'launcher-mask-preview.png')
    print('Generated legacy icons for 5 densities, adaptive foreground and monochrome art.')


if __name__ == '__main__':
    generate()
