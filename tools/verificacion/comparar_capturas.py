import sys, os
from PIL import Image, ImageChops
a, b = sys.argv[1], sys.argv[2]
UMBRAL = int(sys.argv[3]) if len(sys.argv) > 3 else 40
fa = {f for f in os.listdir(a) if f.endswith('.png') and not f.startswith('DEBUG')}
fb = {f for f in os.listdir(b) if f.endswith('.png') and not f.startswith('DEBUG')}
for f in sorted(fa ^ fb): print('SOLO EN UNO:', f)
ok = 0
for f in sorted(fa & fb):
    ia, ib = Image.open(os.path.join(a, f)).convert('RGB'), Image.open(os.path.join(b, f)).convert('RGB')
    if ia.size != ib.size: print('TAMANO', f); continue
    d = ImageChops.difference(ia, ib).convert('L')
    fuerte = d.point(lambda v: 255 if v > UMBRAL else 0)
    bb = fuerte.getbbox()
    if bb:
        n = sum(1 for v in fuerte.getdata() if v)
        print(f'DIFIERE {f} bbox={bb} pix={n}')
    else: ok += 1
print('iguales (umbral', UMBRAL, '):', ok)
