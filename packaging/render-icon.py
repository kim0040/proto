# 앱 아이콘 PNG(src/main/resources/proto/ui/appicon/icon-N.png)를 그린다. 실행: python3 packaging/render-icon.py src/main/resources/proto/ui/appicon
# 디자인은 GPT 이미지 생성 시안(흑백 미니멀 달력)을 따라 가장자리가 깨끗한 도형으로 다시 그린 것이다. 그린 뒤 packaging/make-icons.sh로 .icns/.ico를 만든다
from PIL import Image, ImageDraw
import sys
K = 4
N = 1024 * K
def u(v): return int(round(v * K))
img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
# 타일: 1024 캔버스에 824 크기 (macOS 아이콘 격자), 모서리 186
t0, t1 = 100, 924
# 흰 타일에 검은 달력. 밝은 배경(흰 바탕화면, 밝은 Dock)에서도 모양이 보이도록 옅은 회색 테두리를 두른다
d.rounded_rectangle([u(t0), u(t0), u(t1), u(t1)], radius=u(186), fill=(214, 214, 214, 255))
d.rounded_rectangle([u(t0 + 6), u(t0 + 6), u(t1 - 6), u(t1 - 6)], radius=u(180), fill=(255, 255, 255, 255))
# W = 그림 색(검정), B = 타일 색(흰색). 예전 흑백 반전 전 이름을 그대로 쓴다
W = (17, 17, 17, 255)
B = (255, 255, 255, 255)
# 달력 몸통: 흰 둥근 네모를 칠하고 안쪽을 다시 검게 칠해 굵은 테두리를 만든다
x0, y0, x1, y1 = 282, 318, 742, 742
stroke = 46
d.rounded_rectangle([u(x0), u(y0), u(x1), u(y1)], radius=u(70), fill=W)
d.rounded_rectangle([u(x0 + stroke), u(y0 + stroke), u(x1 - stroke), u(y1 - stroke)], radius=u(70 - stroke + 10), fill=B)
# 머리띠: 위쪽을 흰색으로 채운다
d.rectangle([u(x0 + stroke - 2), u(y0 + stroke - 2), u(x1 - stroke + 2), u(438)], fill=W)
d.rounded_rectangle([u(x0), u(y0), u(x1), u(470)], radius=u(70), fill=W)
# 고리 두 개: 몸통 위로 솟은 둥근 막대 (머리띠 안쪽에 검은 틈을 두어 떠 보이게)
for cx in (402, 622):
    d.rounded_rectangle([u(cx - 46), u(262), u(cx + 46), u(398)], radius=u(46), fill=B)
    d.rounded_rectangle([u(cx - 24), u(262), u(cx + 24), u(376)], radius=u(24), fill=W)
# 오늘 표시: 오른쪽 아래 칸에 흰 네모 하나
d.rounded_rectangle([u(560), u(560), u(660), u(660)], radius=u(18), fill=W)
big = img.resize((1024, 1024), Image.LANCZOS)
out = sys.argv[1]
for s in (16, 32, 48, 64, 128, 256, 512, 1024):
    big.resize((s, s), Image.LANCZOS).save(f'{out}/icon-{s}.png')
