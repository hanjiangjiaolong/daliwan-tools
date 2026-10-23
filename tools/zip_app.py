"""把 jpackage 产出的应用目录压成一个 zip，方便分发。

用法：python tools/zip_app.py
产物：release/daliwan_tool-windows-x64.zip（内含 daliwan_tool/ 目录，解压后直接双击 大力丸.exe）
"""
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "dist", "daliwan_tool")
OUT_DIR = os.path.join(ROOT, "release")
OUT = os.path.join(OUT_DIR, "daliwan_tool-windows-x64.zip")


def main() -> int:
    if not os.path.isdir(SRC):
        print("[错误] 找不到应用目录：%s" % SRC)
        return 1
    os.makedirs(OUT_DIR, exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)

    total = 0
    # 用最高级别的 deflate：内容里大部分是已压缩过的 jar 与模块映像，
    # 再换算法收益有限，能省一点是一点
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for base, _dirs, files in os.walk(SRC):
            for name in files:
                path = os.path.join(base, name)
                arc = os.path.relpath(path, os.path.dirname(SRC))
                zf.write(path, arc.replace(os.sep, "/"))
                total += 1
    size = os.path.getsize(OUT)
    print("[完成] %s" % OUT)
    print("       文件数 %d，大小 %.1f MB" % (total, size / 1024 / 1024))
    if size > 100 * 1024 * 1024:
        print("[警告] 超过 100 MB，Gitee 单文件上限传不上去，需要先瘦身再打包")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
