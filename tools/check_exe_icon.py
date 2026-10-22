# -*- coding: utf-8 -*-
"""诊断 exe 嵌入图标：解析 PE 资源表里的 RT_GROUP_ICON/RT_ICON，导出各档并与源 ICO 对比。"""
import io
import os
import struct
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXE = os.path.join(ROOT, 'dist', 'daliwan_tool', '大力丸.exe')
ICO = os.path.join(ROOT, 'build', 'icon.ico')
OUT = os.path.join(ROOT, 'build', 'verify')


def read_ico(path):
    with open(path, 'rb') as f:
        data = f.read()
    n = struct.unpack_from('<H', data, 4)[0]
    entries = []
    for i in range(n):
        off = 6 + i * 16
        w, h, colors, res, planes, bpp, size, off_data = struct.unpack_from('<BBBBHHII', data, off)
        entries.append({'w': w or 256, 'h': h or 256, 'bpp': bpp, 'size': size, 'off': off_data})
    return data, entries


def pe_resources(exe_path):
    """返回 (rt_group_icons: [(name, bytes)], rt_icons: {id: bytes})，极简 PE 解析。"""
    with open(exe_path, 'rb') as f:
        pe = f.read()
    e_lfanew = struct.unpack_from('<I', pe, 0x3C)[0]
    assert pe[e_lfanew:e_lfanew + 4] == b'PE\0\0'
    coff = e_lfanew + 4
    n_sections = struct.unpack_from('<H', pe, coff + 2)[0]
    opt_size = struct.unpack_from('<H', pe, coff + 16)[0]
    opt = coff + 20
    magic = struct.unpack_from('<H', pe, opt)[0]
    # PE32+ data directories at opt+112, PE32 at opt+96
    dd = opt + (112 if magic == 0x20B else 96)
    rsrc_rva, rsrc_size = struct.unpack_from('<II', pe, dd + 2 * 8)
    sections = []
    sec = opt + opt_size
    for i in range(n_sections):
        name = pe[sec:sec + 8].rstrip(b'\0')
        vsize, vaddr, rsize, raw = struct.unpack_from('<IIII', pe, sec + 8)
        sections.append((name, vaddr, vsize, raw, rsize))
        sec += 40

    def rva2off(rva):
        for name, vaddr, vsize, raw, rsize in sections:
            if vaddr <= rva < vaddr + max(vsize, rsize):
                return raw + (rva - vaddr)
        raise ValueError('rva %x not in sections' % rva)

    base = rva2off(rsrc_rva)

    def parse_dir(rva_dir, level=0, path=()):
        off = rva2off(rva_dir)
        n_named, n_id = struct.unpack_from('<HH', pe, off + 12)
        out = []
        for i in range(n_named + n_id):
            eoff = off + 16 + i * 8
            name, entry = struct.unpack_from('<II', pe, eoff)
            is_dir = bool(entry & 0x80000000)
            entry &= 0x7FFFFFFF
            ident = name & 0x7FFFFFFF
            if is_dir:
                out += parse_dir(rsrc_rva + entry, level + 1, path + (ident,))
            else:
                data_entry = rva2off(rsrc_rva + entry)
                data_rva, size, codepage = struct.unpack_from('<III', pe, data_entry)
                blob = pe[rva2off(data_rva):rva2off(data_rva) + size]
                out.append((path + (ident,), blob))
        return out

    leaves = parse_dir(rsrc_rva)
    groups, icons = [], {}
    for path, blob in leaves:
        if len(path) >= 3 and path[0] == 14:  # RT_GROUP_ICON
            groups.append((path[1], blob))
        elif len(path) >= 3 and path[0] == 3:  # RT_ICON
            icons[path[1]] = blob
    return groups, icons


def main():
    os.makedirs(OUT, exist_ok=True)
    print('=== source ICO ===')
    data, entries = read_ico(ICO)
    for e in entries:
        print('  %3dx%-3d bpp=%-3d blob=%d' % (e['w'], e['h'], e['bpp'], e['size']))
    # 逐帧导出源 ICO
    ico = Image.open(ICO)
    for i in range(getattr(ico, 'n_frames', 1)):
        ico.seek(i)
        ico.save(os.path.join(OUT, 'src-ico-frame%d-%dx%d.png' % (i, ico.size[0], ico.size[1])))
    print('  frames:', getattr(ico, 'n_frames', 1))

    print('=== exe PE resources ===')
    groups, icons = pe_resources(EXE)
    print('  RT_GROUP_ICON count:', len(groups), ' RT_ICON count:', len(icons))
    for gname, gblob in groups:
        n = struct.unpack_from('<H', gblob, 4)[0]
        print('  group id=%s entries=%d' % (gname, n))
        for i in range(n):
            # GRPICONDIRENTRY 固定 14 字节：w,h,color,res,planes,bpp,bytes(4),id(2)
            w, h, colors, res, planes, bpp, size, rid = struct.unpack_from('<BBBBHHIH', gblob, 6 + i * 14)
            blob = icons.get(rid, b'')
            kind = 'PNG' if blob[:8] == b'\x89PNG\r\n\x1a\n' else 'BMP'
            print('    id=%-4d %3dx%-3d bpp=%-3d %s bytes=%d' % (rid, w or 256, h or 256, bpp, kind, len(blob)))
            fn = os.path.join(OUT, 'exe-icon-%dx%d.%s' % (w or 256, h or 256, 'png' if kind == 'PNG' else 'bmp'))
            with open(fn, 'wb') as f:
                f.write(blob)
            if kind == 'PNG':
                im = Image.open(io.BytesIO(blob))
                im.save(os.path.join(OUT, 'exe-icon-%dx%d-check.png' % im.size))
    print('exported to', OUT)


if __name__ == '__main__':
    main()
