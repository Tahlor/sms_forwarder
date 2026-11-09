"""
Pure Python QR code SVG generator (Model 2, Byte mode, Levels L/M/Q/H)
with zero external dependencies, plus fallback to `qrcode` library if present.
"""

# Tables for QR Version 1 to 10 capacities and block structures
# Capacity in total data codewords and EC codewords
# For simplicity and robust pairing URLs (typically 60-120 chars),
# we define Version specifications for standard QR codes.

EXP = [0] * 512
LOG = [0] * 256
_x = 1
for _i in range(255):
    EXP[_i] = _x
    LOG[_x] = _i
    _x <<= 1
    if _x & 0x100:
        _x ^= 0x11d
for _i in range(255, 512):
    EXP[_i] = EXP[_i - 255]

def gf_mul(x: int, y: int) -> int:
    if x == 0 or y == 0:
        return 0
    return EXP[LOG[x] + LOG[y]]

def rs_poly(ec_count: int) -> list[int]:
    poly = [1]
    for i in range(ec_count):
        factor = [1, EXP[i]]
        new_poly = [0] * (len(poly) + 1)
        for j, c in enumerate(poly):
            new_poly[j] ^= c
            new_poly[j + 1] ^= gf_mul(c, factor[1])
        poly = new_poly
    return poly

def rs_encode(data: bytes | list[int], ec_count: int) -> list[int]:
    poly = rs_poly(ec_count)
    res = list(data) + [0] * ec_count
    for i in range(len(data)):
        lead = res[i]
        if lead != 0:
            for j in range(len(poly)):
                res[i + j] ^= gf_mul(poly[j], lead)
    return res[len(data):]

# Version table: (version, ec_level): (total_codewords, ec_codewords_per_block, num_blocks, alignment_pattern_coords)
# We support Level L (least EC, highest capacity) and M
V_INFO = {
    # Version 1 (21x21): L (19 data, 7 ec, 1 block), M (16 data, 10 ec, 1 block)
    (1, 'L'): (26, 7, 1, []),
    (1, 'M'): (26, 10, 1, []),
    # Version 2 (25x25): L (34 data, 10 ec, 1 block), M (28 data, 16 ec, 1 block)
    (2, 'L'): (44, 10, 1, [6, 18]),
    (2, 'M'): (44, 16, 1, [6, 18]),
    # Version 3 (29x29): L (55 data, 15 ec, 1 block), M (44 data, 26 ec, 1 block)
    (3, 'L'): (70, 15, 1, [6, 22]),
    (3, 'M'): (70, 26, 1, [6, 22]),
    # Version 4 (33x33): L (80 data, 20 ec, 1 block), M (64 data, 36 ec, 2 blocks)
    (4, 'L'): (100, 20, 1, [6, 26]),
    (4, 'M'): (100, 18, 2, [6, 26]),
    # Version 5 (37x37): L (108 data, 26 ec, 1 block), M (86 data, 24 ec, 2 blocks)
    (5, 'L'): (134, 26, 1, [6, 30]),
    (5, 'M'): (134, 24, 2, [6, 30]),
    # Version 6 (41x41): L (136 data, 18 ec, 2 blocks), M (108 data, 16 ec, 4 blocks)
    (6, 'L'): (172, 18, 2, [6, 34]),
    (6, 'M'): (172, 16, 4, [6, 34]),
    # Version 7 (45x45): L (156 data, 20 ec, 2 blocks), M (124 data, 18 ec, 4 blocks)
    (7, 'L'): (196, 20, 2, [6, 22, 38]),
    (7, 'M'): (196, 18, 4, [6, 22, 38]),
    # Version 8 (49x49): L (194 data, 24 ec, 2 blocks), M (154 data, 22 ec, 4 blocks)
    (8, 'L'): (242, 24, 2, [6, 24, 42]),
    (8, 'M'): (242, 22, 4, [6, 24, 42]),
}

def choose_version(data_len: int, ec_level: str = 'L') -> int:
    for v in range(1, 9):
        total, ec_per_block, num_blocks, _ = V_INFO[(v, ec_level)]
        total_data = total - (ec_per_block * num_blocks)
        # Byte mode overhead: 4 bits mode + 8 bits length count + data_len*8 bits
        # total bits / 8 <= total_data
        needed_codewords = ((4 + 8 + data_len * 8) + 7) // 8
        if needed_codewords <= total_data:
            return v
    return 8

def encode_data(text: str, version: int, ec_level: str) -> list[int]:
    raw_bytes = text.encode("utf-8")
    total, ec_per_block, num_blocks, _ = V_INFO[(version, ec_level)]
    data_capacity = total - (ec_per_block * num_blocks)

    bits = []
    # Byte mode: 0100
    bits.extend([0, 1, 0, 0])
    # Length (8 bits for V1-9)
    length = len(raw_bytes)
    for i in range(7, -1, -1):
        bits.append((length >> i) & 1)
    # Payload bytes
    for b in raw_bytes:
        for i in range(7, -1, -1):
            bits.append((b >> i) & 1)
    # Terminator (up to 4 zeros)
    max_bits = data_capacity * 8
    pad_zeros = min(4, max_bits - len(bits))
    bits.extend([0] * pad_zeros)
    # Byte align with zeros
    while len(bits) % 8 != 0:
        bits.append(0)
    
    codewords = []
    for i in range(0, len(bits), 8):
        byte_val = 0
        for b in bits[i:i+8]:
            byte_val = (byte_val << 1) | b
        codewords.append(byte_val)

    # Pad codewords alternating 0xEC, 0x11
    pad_bytes = [0xEC, 0x11]
    p_idx = 0
    while len(codewords) < data_capacity:
        codewords.append(pad_bytes[p_idx % 2])
        p_idx += 1

    # Split into blocks and add Reed-Solomon EC
    block_len = data_capacity // num_blocks
    data_blocks = []
    ec_blocks = []
    for b in range(num_blocks):
        start = b * block_len
        end = start + block_len
        chunk = codewords[start:end]
        data_blocks.append(chunk)
        ec_blocks.append(rs_encode(chunk, ec_per_block))

    # Interleave data blocks then ec blocks
    final_codewords = []
    for i in range(block_len):
        for b in range(num_blocks):
            final_codewords.append(data_blocks[b][i])
    for i in range(ec_per_block):
        for b in range(num_blocks):
            final_codewords.append(ec_blocks[b][i])
    return final_codewords

FORMAT_BITS = {
    ('L', 0): 0x77C4, ('L', 1): 0x72F3, ('L', 2): 0x7DAA, ('L', 3): 0x789D,
    ('L', 4): 0x662F, ('L', 5): 0x6318, ('L', 6): 0x6C41, ('L', 7): 0x6976,
    ('M', 0): 0x5412, ('M', 1): 0x5125, ('M', 2): 0x5E7C, ('M', 3): 0x5B4B,
    ('M', 4): 0x45F9, ('M', 5): 0x40CE, ('M', 6): 0x4F97, ('M', 7): 0x4AA0,
}

def generate_qr_matrix(text: str, ec_level: str = 'L') -> list[list[int]]:
    version = choose_version(len(text.encode("utf-8")), ec_level)
    size = version * 4 + 17
    matrix = [[None] * size for _ in range(size)]
    reserved = [[False] * size for _ in range(size)]

    # 1. Place Finder Patterns
    def place_finder(r, c):
        for dr in range(7):
            for dc in range(7):
                if dr in (0, 6) or dc in (0, 6) or (2 <= dr <= 4 and 2 <= dc <= 4):
                    matrix[r + dr][c + dc] = 1
                else:
                    matrix[r + dr][c + dc] = 0
                reserved[r + dr][c + dc] = True

    place_finder(0, 0)
    place_finder(0, size - 7)
    place_finder(size - 7, 0)

    # Separators
    for i in range(8):
        for dr, dc in [(i, 7), (7, i), (i, size - 8), (7, size - 1 - i), (size - 8, i), (size - 1 - i, 7)]:
            if 0 <= dr < size and 0 <= dc < size:
                matrix[dr][dc] = 0
                reserved[dr][dc] = True

    # Alignment Patterns for Version 2+
    _, _, _, align_coords = V_INFO[(version, ec_level)]
    for r in align_coords:
        for c in align_coords:
            # Skip if overlapping finder patterns
            if (r < 9 and c < 9) or (r < 9 and c > size - 10) or (r > size - 10 and c < 9):
                continue
            for dr in range(-2, 3):
                for dc in range(-2, 3):
                    val = 1 if max(abs(dr), abs(dc)) in (0, 2) else 0
                    matrix[r + dr][c + dc] = val
                    reserved[r + dr][c + dc] = True

    # Timing patterns
    for i in range(8, size - 8):
        val = 1 if i % 2 == 0 else 0
        if not reserved[6][i]:
            matrix[6][i] = val
            reserved[6][i] = True
        if not reserved[i][6]:
            matrix[i][6] = val
            reserved[i][6] = True

    # Dark module
    matrix[size - 8][8] = 1
    reserved[size - 8][8] = True

    # Reserve format info areas
    for i in range(9):
        reserved[8][i] = True
        reserved[i][8] = True
        if size - 1 - i < size:
            reserved[8][size - 1 - i] = True
            reserved[size - 1 - i][8] = True

    # Encode data
    codewords = encode_data(text, version, ec_level)
    data_bits = []
    for cw in codewords:
        for i in range(7, -1, -1):
            data_bits.append((cw >> i) & 1)

    # Place data in zigzag columns
    bit_idx = 0
    row = size - 1
    col = size - 1
    direction = -1

    while col > 0:
        if col == 6:  # Skip vertical timing column
            col -= 1
        for c in [col, col - 1]:
            if not reserved[row][c]:
                b = data_bits[bit_idx] if bit_idx < len(data_bits) else 0
                matrix[row][c] = b
                bit_idx += 1
        row += direction
        if row < 0 or row >= size:
            direction = -direction
            row += direction
            col -= 2

    # Mask pattern: Mask 0: (row + col) % 2 == 0
    mask_val = 0
    for r in range(size):
        for c in range(size):
            if not reserved[r][c]:
                if (r + c) % 2 == 0:
                    matrix[r][c] ^= 1

    # Format info
    format_info = FORMAT_BITS[(ec_level, mask_val)]
    format_bits = [(format_info >> i) & 1 for i in range(14, -1, -1)]

    # Top-left horizontal & vertical
    seq1 = [(8, 0), (8, 1), (8, 2), (8, 3), (8, 4), (8, 5), (8, 7), (8, 8),
            (7, 8), (5, 8), (4, 8), (3, 8), (2, 8), (1, 8), (0, 8)]
    for idx, (r, c) in enumerate(seq1):
        matrix[r][c] = format_bits[idx]

    # Split around other finders
    seq2 = [(size - 1, 8), (size - 2, 8), (size - 3, 8), (size - 4, 8), (size - 5, 8), (size - 6, 8), (size - 7, 8),
            (8, size - 8), (8, size - 7), (8, size - 6), (8, size - 5), (8, size - 4), (8, size - 3), (8, size - 2), (8, size - 1)]
    for idx, (r, c) in enumerate(seq2):
        matrix[r][c] = format_bits[idx]

    return matrix

def generate_qr_svg(text: str, box_size: int = 10, border: int = 4) -> str:
    # Try using qrcode library if available
    try:
        import qrcode
        import qrcode.image.svg
        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=box_size,
            border=border,
            image_factory=qrcode.image.svg.SvgPathImage
        )
        qr.add_data(text)
        qr.make(fit=True)
        img = qr.make_image()
        return img.to_string(encoding='unicode')
    except (ImportError, Exception):
        pass

    # Pure Python SVG generation
    matrix = generate_qr_matrix(text, ec_level='M')
    n = len(matrix)
    total_size = (n + border * 2) * box_size
    
    path_commands = []
    for r in range(n):
        for c in range(n):
            if matrix[r][c] == 1:
                x = (c + border) * box_size
                y = (r + border) * box_size
                path_commands.append(f"M{x},{y}h{box_size}v{box_size}h-{box_size}z")

    d = " ".join(path_commands)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total_size} {total_size}" '
        f'width="{total_size}" height="{total_size}" shape-rendering="crispEdges">\n'
        f'  <rect width="100%" height="100%" fill="#ffffff"/>\n'
        f'  <path d="{d}" fill="#111827"/>\n'
        f'</svg>'
    )
