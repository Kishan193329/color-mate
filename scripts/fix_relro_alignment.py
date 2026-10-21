#!/usr/bin/env python3
"""
Fix RELRO 16KB alignment in pre-built Android .so files.
Extends PT_GNU_RELRO and containing PT_LOAD segments so RELRO end is 16KB-aligned.
Uses only Python standard library (struct).
"""
import os
import sys
import struct

PAGE_ALIGN = 16384  # 16 KB
PT_GNU_RELRO = 0x6474e552


def align_up(value: int, alignment: int) -> int:
    return (value + alignment - 1) & ~(alignment - 1)


def fix_elf_relro(input_path: str, output_path: str) -> bool:
    """Fix RELRO alignment. Returns True if file was modified."""
    with open(input_path, "rb") as f:
        data = bytearray(f.read())

    if data[:4] != b"\x7fELF":
        return False

    # Parse ELF header
    is_64 = data[4] == 2
    little_endian = data[5] == 1
    endian = "<" if little_endian else ">"
    fmt = "Q" if is_64 else "I"
    phoff = struct.unpack_from(endian + fmt, data, 0x20 if is_64 else 0x1C)[0]
    phentsize = struct.unpack_from(endian + "H", data, 0x36 if is_64 else 0x2A)[0]
    phnum = struct.unpack_from(endian + "H", data, 0x38 if is_64 else 0x2C)[0]

    relro_seg_idx = None
    load_seg_idx = None
    relro_end = 0
    load_end_file = 0
    load_end_mem = 0
    load_offset = 0
    load_vaddr = 0

    # Collect PT_LOAD segments and find PT_GNU_RELRO (RELRO comes after PT_LOAD in typical ELF)
    load_segments = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type == PT_GNU_RELRO:
            p_vaddr = struct.unpack_from(endian + fmt, data, off + (0x10 if is_64 else 0x8))[0]
            p_memsz = struct.unpack_from(endian + fmt, data, off + (0x28 if is_64 else 0x14))[0]
            relro_end = p_vaddr + p_memsz
            relro_seg_idx = i
        elif p_type == 1:  # PT_LOAD
            p_offset = struct.unpack_from(endian + fmt, data, off + (0x8 if is_64 else 0x4))[0]
            p_vaddr = struct.unpack_from(endian + fmt, data, off + (0x10 if is_64 else 0x8))[0]
            p_filesz = struct.unpack_from(endian + fmt, data, off + (0x20 if is_64 else 0x10))[0]
            p_memsz = struct.unpack_from(endian + fmt, data, off + (0x28 if is_64 else 0x14))[0]
            load_segments.append((i, p_offset, p_vaddr, p_filesz, p_memsz))

    # Find PT_LOAD that contains RELRO end
    if relro_seg_idx is None:
        return False
    for i, p_offset, p_vaddr, p_filesz, p_memsz in load_segments:
        if p_vaddr <= relro_end <= p_vaddr + p_memsz:
            load_seg_idx = i
            load_offset = p_offset
            load_vaddr = p_vaddr
            load_end_file = p_offset + p_filesz
            load_end_mem = p_vaddr + p_memsz
            break
    if load_seg_idx is None:
        return False

    new_relro_end = align_up(relro_end, PAGE_ALIGN)
    if new_relro_end == relro_end:
        return False  # Already aligned

    padding = new_relro_end - relro_end
    new_load_filesz = (load_end_file - load_offset) + padding
    new_load_memsz = (load_end_mem - load_vaddr) + padding
    relro_off = phoff + relro_seg_idx * phentsize
    relro_vaddr = struct.unpack_from(endian + fmt, data, relro_off + (0x10 if is_64 else 0x8))[0]
    new_relro_memsz = new_relro_end - relro_vaddr

    # Insert padding at end of load segment in file
    insert_pos = int(load_end_file)
    padding_bytes = b"\x00" * padding
    data = data[:insert_pos] + padding_bytes + data[insert_pos:]

    # Update offsets for content after insert (program headers, section headers)
    if phoff >= insert_pos:
        phoff += padding
        struct.pack_into(endian + fmt, data, 0x20 if is_64 else 0x1C, phoff)
    e_shoff = struct.unpack_from(endian + fmt, data, 0x28 if is_64 else 0x20)[0]
    if e_shoff > 0 and e_shoff >= insert_pos:
        struct.pack_into(endian + fmt, data, 0x28 if is_64 else 0x20, e_shoff + padding)
    for i in range(phnum):
        off = phoff + i * phentsize
        p_offset = struct.unpack_from(endian + fmt, data, off + (0x8 if is_64 else 0x4))[0]
        if p_offset > insert_pos:
            struct.pack_into(endian + fmt, data, off + (0x8 if is_64 else 0x4), p_offset + padding)

    # Update PT_LOAD segment
    load_off = phoff + load_seg_idx * phentsize
    struct.pack_into(endian + fmt, data, load_off + (0x20 if is_64 else 0x10), new_load_filesz)
    struct.pack_into(endian + fmt, data, load_off + (0x28 if is_64 else 0x14), new_load_memsz)

    # Update PT_GNU_RELRO segment
    struct.pack_into(endian + fmt, data, relro_off + (0x28 if is_64 else 0x14), new_relro_memsz)

    with open(output_path, "wb") as f:
        f.write(data)
    return True


def main():
    if len(sys.argv) < 2:
        print("Usage: fix_relro_alignment.py <jniLibs_dir>", file=sys.stderr)
        sys.exit(1)

    jni_dir = sys.argv[1]
    libs_to_fix = ("libscanner.so", "libeditor.so", "libpdfium.so")
    fixed = 0

    for root, _, files in os.walk(jni_dir):
        for f in files:
            if f in libs_to_fix:
                path = os.path.join(root, f)
                tmp = path + ".aligned"
                try:
                    if fix_elf_relro(path, tmp):
                        os.replace(tmp, path)
                        fixed += 1
                        print(f"Fixed: {path}")
                    elif os.path.exists(tmp):
                        os.remove(tmp)
                except Exception as e:
                    if os.path.exists(tmp):
                        os.remove(tmp)
                    print(f"Skip {path}: {e}", file=sys.stderr)

    print(f"Fixed {fixed} library(ies)")
    sys.exit(0)


if __name__ == "__main__":
    main()
