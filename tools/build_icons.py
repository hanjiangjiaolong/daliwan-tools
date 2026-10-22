#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
icon 打包流水线：把写实锤子产品图合成为应用图标。

用法：
    python tools/build_icons.py [输出目录]   # 默认 build/

输入：tools/icon-source.png（写实羊角锤产品图，白底）
流程：抠除白底 → 去白边毛边 → 柔和投影 → 合成 Xcode 风凸面蓝色圆角底
      → 输出 7 档 PNG + ICO
改图标：替换 tools/icon-source.png 后重跑本脚本即可（不需要改代码）。
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, 'tools', 'icon-source.png')
SIZES = [16, 24, 32, 48, 64, 128, 256]
MASTER = 512  # 主画布分辨率，再缩小到各档，保证小尺寸抗锯齿

# 液态宝蓝（采样自官方 Xcode 图标：hue≈207、sat≈0.86 的青蓝，不是靛蓝）
BG_TOP = (40, 166, 236)      # #28A6EC 液面受光
BG_BOTTOM = (16, 108, 214)   # #106CD6 液体深处
EDGE_DEEP = (12, 88, 186)    # 边缘纵深（液体内凹感）
SHEEN = (126, 208, 255)      # 顶部液面高光（青调，不用白，保住饱和度）


def key_out_white(img: Image.Image) -> Image.Image:
    """从四边种子点泛洪填充去掉白底与软阴影，保留主体（含高光）。"""
    img = img.convert('RGBA')
    w, h = img.size
    px = img.load()
    # 泛洪参数：与种子白色差异 ≤42 视为背景（能吃掉浅灰软影，不穿透铬面暗边）
    seeds = []
    step = max(8, w // 64)
    for x in range(0, w, step):
        seeds += [(x, 0), (x, h - 1)]
    for y in range(0, h, step):
        seeds += [(0, y), (w - 1, y)]

    def is_bg(x, y):
        r, g, b, a = px[x, y]
        return a > 0 and (255 - min(r, g, b)) <= 42 and max(r, g, b) - min(r, g, b) <= 18

    visited = bytearray(w * h)
    stack = [s for s in seeds if is_bg(*s)]
    for s in stack:
        visited[s[1] * w + s[0]] = 1
    while stack:
        x, y = stack.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[ny * w + nx] and is_bg(nx, ny):
                visited[ny * w + nx] = 1
                stack.append((nx, ny))
    out = img.copy()
    opx = out.load()
    for y in range(h):
        base = y * w
        for x in range(w):
            if visited[base + x]:
                opx[x, y] = (255, 255, 255, 0)
    return out


def defringe(img: Image.Image) -> Image.Image:
    """去掉白边毛边：alpha 内缩切掉白描边环，再轻微羽化让边缘顺滑。

    抠图后主体轮廓常留一圈「白底×主体」的浅色过渡像素，直接贴到蓝底上
    就是毛边。把 alpha 向内收 2px 去掉这圈，然后羽化 1px 恢复柔和感。
    """
    r, g, b, a = img.split()
    a = a.filter(ImageFilter.MinFilter(5))   # 5×5 最小值滤波 → 内缩约 2px
    a = a.filter(ImageFilter.GaussianBlur(1.0))
    return Image.merge('RGBA', (r, g, b, a))


def tone_down_glare(img: Image.Image) -> Image.Image:
    """去掉金属贼光：高光滚降 + 镜面反射柔化（木柄亮度低，不受影响）。

    1) 高光滚降：≥188 的亮度按软曲线压缩，刺眼炸白 → 亮灰缎面
    2) 反射柔化：只在「亮且离轮廓 ≥4px」的区域轻模糊 RGB，
       避免把背景色糊进边缘重新产生毛边
    """
    t, k = 172, 42

    def rolloff(L):
        if L <= t:
            return L
        d = L - t
        return int(t + d / (1.0 + d / k))

    lut = [rolloff(L) for L in range(256)]
    r, g, b, a = img.split()
    r, g, b = r.point(lut), g.point(lut), b.point(lut)

    rgb = Image.merge('RGB', (r, g, b))
    rgb_soft = rgb.filter(ImageFilter.GaussianBlur(3.2))
    rs, gs, bs = rgb_soft.split()
    soft = Image.merge('RGBA', (rs, gs, bs, a))

    # 亮区掩码（滚降后仍偏亮的镜面反射）
    bright = rgb.convert('L').point(lambda v: 255 if v > 168 else 0)
    # 安全区掩码（轮廓内 ≥4px，防止模糊跨界吸色）
    safe = a.filter(ImageFilter.MinFilter(9)).point(lambda v: 255 if v > 128 else 0)
    mask = ImageChops.multiply(bright, safe)

    return Image.composite(soft, Image.merge('RGBA', (r, g, b, a)), mask)


def trim(img: Image.Image) -> Image.Image:
    """裁掉全透明边，便于居中排版。"""
    bbox = img.getbbox()
    return img.crop(bbox) if bbox else img


def rounded_bg(size: int) -> Image.Image:
    """液态蓝色圆角底（Xcode 同款质感）：高饱和青蓝液体 + 边缘纵深 + 液面高光。"""
    radius = int(size * 0.224)
    grad = Image.new('RGBA', (size, size))
    gd = ImageDraw.Draw(grad)

    # 1) 基础竖向渐变：液面 → 深处（Xcode 蓝实测渐变曲线）
    for y in range(size):
        t = (y / max(1, size - 1)) ** 1.05
        r = int(BG_TOP[0] + (BG_BOTTOM[0] - BG_TOP[0]) * t)
        g = int(BG_TOP[1] + (BG_BOTTOM[1] - BG_TOP[1]) * t)
        b = int(BG_TOP[2] + (BG_BOTTOM[2] - BG_TOP[2]) * t)
        gd.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # 2) 边缘纵深：内侧一圈深蓝、大模糊，模拟液体厚度（中心保持饱和不被洗淡）
    vig = Image.new('L', (size, size), 0)
    vd = ImageDraw.Draw(vig)
    inset = int(size * 0.055)
    vd.rounded_rectangle([inset, inset, size - 1 - inset, size - 1 - inset],
                         radius=max(1, radius - inset), fill=255)
    vig = ImageOps.invert(vig).filter(ImageFilter.GaussianBlur(size * 0.075))
    vig = vig.point(lambda v: int(v * 0.55))  # 纵深克制，不压黑
    grad.paste(Image.new('RGBA', (size, size), EDGE_DEEP + (255,)), (0, 0), vig)

    # 3) 液面高光：顶部窄椭圆，青调光（不用白，避免饱和度被冲掉）
    sheen = Image.new('L', (size, size), 0)
    ImageDraw.Draw(sheen).ellipse(
        [-size * 0.12, -size * 0.42, size * 1.12, size * 0.34], fill=90)
    sheen = sheen.filter(ImageFilter.GaussianBlur(size * 0.10))
    grad.paste(Image.new('RGBA', (size, size), SHEEN + (255,)), (0, 0), sheen)

    # 干净圆角裁切输出（无描边/外框）
    m2 = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m2).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(grad, (0, 0), m2)
    return out


def compose(size: int, hammer: Image.Image) -> Image.Image:
    """合成：凸面蓝底 + 投影 + 锤子（占画布约 78%）。"""
    canvas = rounded_bg(size)
    pad = int(size * 0.09)
    box = size - pad * 2
    scale = min(box / hammer.width, box / hammer.height)
    hw = max(1, int(hammer.width * scale))
    hh = max(1, int(hammer.height * scale))
    hm = hammer.resize((hw, hh), Image.LANCZOS)
    x = (size - hw) // 2
    y = (size - hh) // 2

    # 柔和投影：偏移 + 高斯模糊的暗蓝影
    if size >= 24:
        shadow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        alpha = hm.split()[3]
        sil = Image.new('RGBA', (hw, hh), (10, 62, 130, 175))
        off = max(1, size // 52)
        shadow.paste(sil, (x + off, y + off + max(1, size // 55)), alpha)
        shadow = shadow.filter(ImageFilter.GaussianBlur(max(1.0, size / 46)))
        canvas.alpha_composite(shadow)

    canvas.alpha_composite(hm, (x, y))
    return canvas


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build')
    os.makedirs(out_dir, exist_ok=True)

    src = Image.open(SOURCE)
    hammer = trim(tone_down_glare(defringe(key_out_white(src))))
    print('source %s -> hammer bbox %s' % (src.size, hammer.size))

    # 主画布 512 出图，再缩到各档（比直接小尺寸绘制更平滑）
    master = compose(MASTER, hammer)
    master.save(os.path.join(out_dir, 'icon-512.png'))

    icons = []
    for s in SIZES:
        img = master.resize((s, s), Image.LANCZOS)
        p = os.path.join(out_dir, 'icon-%d.png' % s)
        img.save(p)
        icons.append(img)
        print('wrote', p)

    ico_path = os.path.join(out_dir, 'icon.ico')
    # ICO 内嵌各档 PNG（Vista+ 支持）
    icons[-1].save(ico_path, format='ICO', sizes=[(s, s) for s in SIZES], append_images=icons[:-1])
    print('wrote', ico_path)
    print('done: %d sizes + ico' % len(SIZES))


if __name__ == '__main__':
    main()
