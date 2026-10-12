import sys, os
from PIL import Image, ImageChops, ImageDraw
a, b, name, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
y0 = int(sys.argv[5]) if len(sys.argv) > 5 else None
y1 = int(sys.argv[6]) if len(sys.argv) > 6 else None
ia = Image.open(os.path.join(a, name)).convert('RGB'); ib = Image.open(os.path.join(b, name)).convert('RGB')
d = ImageChops.difference(ia, ib)
bb = d.getbbox()
if y0 is None:
    y0 = max(0, bb[1] - 60); y1 = min(ia.height, bb[3] + 60)
ca, cb = ia.crop((0, y0, ia.width, y1)), ib.crop((0, y0, ib.width, y1))
m = d.convert('L').point(lambda v: 255 if v else 0).crop((0, y0, ia.width, y1))
W = ia.width
res = Image.new('RGB', (W * 3 + 20, y1 - y0), 'white')
res.paste(ca, (0, 0)); res.paste(cb, (W + 10, 0)); res.paste(Image.merge('RGB', (m, m, m)), (2 * W + 20, 0))
if res.height > 1800:
    res = res.resize((res.width * 1800 // res.height, 1800))
res.save(out)
print(bb)
