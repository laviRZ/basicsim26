"""Generate Block Bot's pivot-local, Z-up AdvantageScope assets (stdlib only)."""

import itertools
import json
import math
import os
from pathlib import Path
import shutil
import struct


class Part:
    def __init__(self, color):
        self.color = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)] + [1]
        self.positions = []
        self.normals = []
        self.indices = []

    def face(self, points):
        a, b, c = points[:3]
        u = [b[i] - a[i] for i in range(3)]
        v = [c[i] - a[i] for i in range(3)]
        normal = [u[1]*v[2] - u[2]*v[1], u[2]*v[0] - u[0]*v[2],
                  u[0]*v[1] - u[1]*v[0]]
        length = math.sqrt(sum(x*x for x in normal))
        normal = [x / length for x in normal]
        start = len(self.positions)
        self.positions.extend(points)
        self.normals.extend([normal] * len(points))
        for i in range(1, len(points) - 1):
            self.indices.extend((start, start + i, start + i + 1))


def box(sx, sy, sz, center, color):
    part = Part(color)
    # Cyclic tangent axes give outward, counter-clockwise faces.
    sizes = (sx, sy, sz)
    for axis in range(3):
        u, v = (axis + 1) % 3, (axis + 2) % 3
        for sign in (-1, 1):
            points = []
            for du, dv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                point = list(center)
                point[axis] += sign * sizes[axis] / 2
                point[u] += du * sizes[u] / 2
                point[v] += dv * sizes[v] / 2
                points.append(point)
            part.face(points if sign == 1 else points[::-1])
    return part


def cylinder(r, length, axis, center, color, segments=24):
    part = Part(color)
    axis = "xyz".index(axis)
    u, v = (axis + 1) % 3, (axis + 2) % 3

    def point(angle, height, radius=r):
        p = list(center)
        p[axis] += height
        p[u] += radius * math.cos(angle)
        p[v] += radius * math.sin(angle)
        return p

    for i in range(segments):
        a, b = i * math.tau / segments, (i + 1) * math.tau / segments
        lo, hi = -length / 2, length / 2
        part.face([point(a, lo), point(b, lo), point(b, hi), point(a, hi)])
        part.face([point(0, hi, 0), point(a, hi), point(b, hi)])
        part.face([point(0, lo, 0), point(b, lo), point(a, lo)])
    return part


def wedge(r, length, axis, center, a0, a1, color, segments=6):
    """A convex sector of a cylinder, from angle a0 to a1 about the axis through center."""
    part = Part(color)
    axis = "xyz".index(axis)
    u, v = (axis + 1) % 3, (axis + 2) % 3

    def point(angle, height, radius=r):
        p = list(center)
        p[axis] += height
        p[u] += radius * math.cos(angle)
        p[v] += radius * math.sin(angle)
        return p

    mid = (a0 + a1) / 2
    inside = point(mid, 0, r / 2)

    def face(points):
        # Wind each face outward: its normal must point away from a point inside the wedge.
        a, b, c = points[:3]
        n = [(b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]),
             (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]),
             (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])]
        if sum(n[k] * (a[k] - inside[k]) for k in range(3)) < 0:
            points = points[::-1]
        part.face(points)

    lo, hi = -length / 2, length / 2
    angles = [a0 + (a1 - a0) * k / segments for k in range(segments + 1)]
    for a, b in zip(angles, angles[1:]):
        face([point(a, lo), point(b, lo), point(b, hi), point(a, hi)])
        face([point(0, hi, 0), point(a, hi), point(b, hi)])
        face([point(0, lo, 0), point(b, lo), point(a, lo)])
    for a in (a0, a1):
        face([point(0, lo, 0), point(a, lo), point(a, hi), point(0, hi, 0)])
    return part


def striped(r, length, axis, center, colors, stripes=4):
    """A cylinder of alternately colored wedges that meet face to face, so its spin shows."""
    step = math.tau / stripes
    return [wedge(r, length, axis, center, k * step, (k + 1) * step, colors[k % len(colors)],
                  segments=max(2, 24 // stripes))
            for k in range(stripes)]


def glb(parts, name):
    binary = bytearray()
    document = {
        "asset": {"version": "2.0", "generator": "basicsim/tools/build_block_bot.py"},
        "scene": 0, "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": name + "_NOSIMPLIFY"}],
        "meshes": [{"name": name + "_NOSIMPLIFY", "primitives": []}],
        "materials": [], "accessors": [], "bufferViews": [], "buffers": [],
    }

    def accessor(values, fmt, kind, component_type, target, bounds=False):
        binary.extend(b"\0" * (-len(binary) % 4))
        offset = len(binary)
        for value in values:
            binary.extend(struct.pack("<" + fmt, *value))
        view = len(document["bufferViews"])
        document["bufferViews"].append({"buffer": 0, "byteOffset": offset,
                                      "byteLength": len(binary) - offset, "target": target})
        entry = {"bufferView": view, "componentType": component_type,
                 "count": len(values), "type": kind}
        if bounds:
            entry["min"] = [min(v[i] for v in values) for i in range(3)]
            entry["max"] = [max(v[i] for v in values) for i in range(3)]
        index = len(document["accessors"])
        document["accessors"].append(entry)
        return index

    for part in parts:
        positions = accessor(part.positions, "3f", "VEC3", 5126, 34962, True)
        normals = accessor(part.normals, "3f", "VEC3", 5126, 34962)
        indices = accessor([(i,) for i in part.indices], "H", "SCALAR", 5123, 34963)
        material = len(document["materials"])
        document["materials"].append({"pbrMetallicRoughness": {
            "baseColorFactor": part.color, "metallicFactor": 0, "roughnessFactor": 1},
            "doubleSided": True})
        document["meshes"][0]["primitives"].append({"attributes": {
            "POSITION": positions, "NORMAL": normals}, "indices": indices, "material": material})
    document["buffers"] = [{"byteLength": len(binary)}]
    text = json.dumps(document, separators=(",", ":")).encode()
    text += b" " * (-len(text) % 4)
    binary.extend(b"\0" * (-len(binary) % 4))
    return (struct.pack("<III", 0x46546C67, 2, 28 + len(text) + len(binary))
            + struct.pack("<II", len(text), 0x4E4F534A) + text
            + struct.pack("<II", len(binary), 0x004E4942) + binary)


def validate(path, expect_x=None):
    """Read actual buffers back; check topology, bounds, normals and GLB framing."""
    data = path.read_bytes()
    assert struct.unpack_from("<III", data) == (0x46546C67, 2, len(data))
    size, kind = struct.unpack_from("<II", data, 12)
    assert kind == 0x4E4F534A and size % 4 == 0
    doc = json.loads(data[20:20 + size])
    bin_size, kind = struct.unpack_from("<II", data, 20 + size)
    assert kind == 0x004E4942 and bin_size % 4 == 0
    binary = data[28 + size:]
    assert len(binary) == bin_size

    def unpack(index, fmt):
        acc = doc["accessors"][index]
        view = doc["bufferViews"][acc["bufferView"]]
        start = view.get("byteOffset", 0)
        end = start + view["byteLength"]
        assert start % 4 == 0 and end <= doc["buffers"][0]["byteLength"]
        values = list(struct.iter_unpack("<" + fmt, binary[start:end]))
        assert len(values) == acc["count"]
        return values

    points = []
    for primitive in doc["meshes"][0]["primitives"]:
        p = unpack(primitive["attributes"]["POSITION"], "3f")
        n = unpack(primitive["attributes"]["NORMAL"], "3f")
        indices = [i[0] for i in unpack(primitive["indices"], "H")]
        assert p and len(p) == len(n) and len(indices) % 3 == 0
        assert all(0 <= i < len(p) for i in indices)
        assert all(abs(sum(x*x for x in normal) - 1) < 1e-5 for normal in n)
        for i in range(0, len(indices), 3):
            a, b, c = (p[j] for j in indices[i:i + 3])
            u, v = [b[k]-a[k] for k in range(3)], [c[k]-a[k] for k in range(3)]
            cross = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
            assert sum(cross[k] * n[indices[i]][k] for k in range(3)) > 0
        points.extend(p)
    lo = [min(p[i] for p in points) for i in range(3)]
    hi = [max(p[i] for p in points) for i in range(3)]
    assert all(-2 <= low < high <= 2 for low, high in zip(lo, hi))
    if expect_x is not None:
        assert abs(lo[0] - expect_x[0]) < 1e-6 and abs(hi[0] - expect_x[1]) < 1e-6
    print(f"{path.name}: min={tuple(round(x, 4) for x in lo)} max={tuple(round(x, 4) for x in hi)}")


def bounds(points):
    return ([min(p[i] for p in points) for i in range(3)],
            [max(p[i] for p in points) for i in range(3)])


def profile(parts, pitch_limit):
    """Convex (radius, height) outline a part set covers while the turret yaws.

    Yaw is unbounded, so a point's radius and height are the only coordinates that matter: yaw
    carries every point of this outline through every angle. Testing the outline against an
    obstacle's own (radius, height) box is therefore the real collision question, without the
    false positives a bounding cylinder gives around the bearing collar.
    """
    points = []
    for part in parts:
        for i in range(17):
            pitch = pitch_limit * (2 * i / 16 - 1)
            c, s = math.cos(pitch), math.sin(pitch)
            for x, y, z in part.positions:
                points.append((math.hypot(x * c + z * s, y), -x * s + z * c))
    points = sorted(set(points))
    def half(seq):
        hull = []
        for p in seq:
            while len(hull) > 1 and ((hull[-1][0] - hull[-2][0]) * (p[1] - hull[-2][1])
                                     - (hull[-1][1] - hull[-2][1]) * (p[0] - hull[-2][0])) <= 0:
                hull.pop()
            hull.append(p)
        return hull
    return half(points)[:-1] + half(points[::-1])[:-1]


def obstacle_profile(box_bounds, center):
    """An axis-aligned world box as a (radius, height) rectangle about the yaw axis."""
    (lox, loy, loz), (hix, hiy, hiz) = box_bounds
    dx = max(lox - center[0], 0.0, center[0] - hix)
    dy = max(loy - center[1], 0.0, center[1] - hiy)
    rmax = max(math.hypot(x - center[0], y - center[1])
               for x in (lox, hix) for y in (loy, hiy))
    return math.hypot(dx, dy), rmax, loz, hiz


def separation(hull, rect):
    """Positive gap between a convex (r, z) hull and an (r, z) box, or a negative overlap.

    Separating-axis test; the largest margin over all candidate axes is a lower bound on the
    true distance, so a positive result is a proof of clearance, not an estimate.
    """
    rmin, rmax, zmin, zmax = rect
    axes = [(1.0, 0.0), (0.0, 1.0)]
    for i, a in enumerate(hull):
        b = hull[(i + 1) % len(hull)]
        length = math.hypot(b[0] - a[0], b[1] - a[1])
        if length:
            axes.append(((b[1] - a[1]) / length, (a[0] - b[0]) / length))
    best = -math.inf
    for ux, uz in axes:
        hs = [p[0] * ux + p[1] * uz for p in hull]
        rs = [r * ux + z * uz for r in (rmin, rmax) for z in (zmin, zmax)]
        gap = max(min(rs) - max(hs), min(hs) - max(rs))
        best = max(best, gap)
    return best


def _planes(part):
    """A convex part's face planes, each once: (unit normal, offset)."""
    seen = {}
    for t in range(0, len(part.indices), 3):
        i = part.indices[t]
        n, q = part.normals[i], part.positions[i]
        d = sum(n[k] * q[k] for k in range(3))
        seen.setdefault((tuple(round(x, 4) for x in n), round(d, 5)), (n, d))
    return list(seen.values())


def _directions(part):
    """A convex part's edge directions, each once, up to sign."""
    seen = set()
    for t in range(0, len(part.indices), 3):
        tri = [part.positions[part.indices[t + k]] for k in range(3)]
        for a, b in ((0, 1), (1, 2), (2, 0)):
            d = [tri[b][k] - tri[a][k] for k in range(3)]
            length = math.sqrt(sum(x * x for x in d))
            if length > 1e-9:
                d = [x / length for x in d]
                if d < [-x for x in d]:
                    d = [-x for x in d]
                seen.add(tuple(round(x, 5) for x in d))
    return list(seen)


def _penetration(a, b):
    """Separating-axis overlap depth of two convex parts; zero or less means apart or touching."""
    axes = [n for n, _ in _planes(a)] + [n for n, _ in _planes(b)]
    for x in _directions(a):
        for y in _directions(b):
            c = (x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0])
            length = math.sqrt(sum(k * k for k in c))
            if length > 1e-6:
                axes.append(tuple(k / length for k in c))
    depth = math.inf
    for ax in axes:
        pa = [sum(q[k] * ax[k] for k in range(3)) for q in a.positions]
        pb = [sum(q[k] * ax[k] for k in range(3)) for q in b.positions]
        depth = min(depth, min(max(pa), max(pb)) - max(min(pa), min(pb)))
        if depth <= 0:
            break
    return depth


def _shared_face(a, b):
    """True when a and b have same-facing triangles in one plane that overlap with area."""
    def tris(part):
        return [([part.positions[part.indices[t + k]] for k in range(3)],
                 part.normals[part.indices[t]]) for t in range(0, len(part.indices), 3)]
    def apart(p, q):
        for poly in (p, q):
            for i in range(3):
                s, e = poly[i], poly[(i + 1) % 3]
                nx, ny = e[1] - s[1], s[0] - e[0]
                pp = [x * nx + y * ny for x, y in p]
                qq = [x * nx + y * ny for x, y in q]
                if min(pp) >= max(qq) - 1e-9 or min(qq) >= max(pp) - 1e-9:
                    return True
        return False
    for ta, na in tris(a):
        da = sum(na[k] * ta[0][k] for k in range(3))
        for tb, nb in tris(b):
            if (sum(na[k] * nb[k] for k in range(3)) > .9999
                    and abs(sum(na[k] * tb[0][k] for k in range(3)) - da) < 1e-6):
                axis = max(range(3), key=lambda k: abs(na[k]))
                u, v = [k for k in range(3) if k != axis]
                if not apart([(q[u], q[v]) for q in ta], [(q[u], q[v]) for q in tb]):
                    return True
    return False


def check_static(parts, label):
    """Within one model file, parts may touch but never poke into each other or share a face.

    A part poking out of another shows its edges where they cross; two parts sharing a face
    flicker as the renderer picks one or the other. A part wholly inside another is invisible
    and allowed.
    """
    for (i, a), (j, b) in itertools.combinations(enumerate(parts), 2):
        (alo, ahi), (blo, bhi) = bounds(a.positions), bounds(b.positions)
        if any(alo[k] > bhi[k] + 1e-9 or blo[k] > ahi[k] + 1e-9 for k in range(3)):
            continue
        depth = _penetration(a, b)
        if depth > 1e-6:
            within = [all(all(sum(n[k] * q[k] for k in range(3)) <= d + 1e-6 for n, d in _planes(o))
                          for q in p.positions) for p, o in ((a, b), (b, a))]
            assert any(within), f"{label}: part {i} pokes {depth * 1000:.1f} mm into part {j}"
        assert not _shared_face(a, b), f"{label}: parts {i} and {j} share a face (flicker)"


def main():
    output = Path(__file__).resolve().parents[1] / "assets" / "Robot_BlockBot"
    output.mkdir(parents=True, exist_ok=True)
    origin = (0, 0, 0)
    gray, dark, tire, bumper = "#636E78", "#3B4249", "#22262A", "#C93B2B"
    # Frame perimeter is y = +/-0.305 and thin bumpers sit outside it at y = +/-0.40. Deck top is
    # z = 0.16. Four swerve modules sit under the deck; each is two components -- a pod that steers
    # about its vertical axis (a fork under the deck and a cap with a pointer above it, so the
    # steer angle reads from above) and a wheel that rolls about its axle.
    # Joints. The turret yaws without limit, so the elevator must lie entirely outside the circle
    # swept by the hood: the carriage's near face is the closest approach, and the barrel is kept
    # short enough to stay inside it. The intake is a hinge at the chassis front: the frame swings
    # 90 degrees between vertical (stowed) and horizontal (deployed) instead of sliding out.
    elevator_x, upright_y, crossbar_z = -.28, .20, .63
    carriage_half_y = .14
    hinge_x, hinge_z = .30, .24
    carriage_z0, carriage_travel = .24, .26
    # Parts meet face to face: no part pokes into another, and no two parts share a face, which
    # would flicker as the renderer picks one or the other. Only a part entirely hidden inside
    # another (the hinge shaft in the frame's tube) may overlap, and check_static() enforces it.
    base = [box(.62, .54, .03, (0, 0, .145), gray),                 # deck plate, on the rails
            box(.05, .61, .07, (-.305, 0, .095), dark),             # rear frame rail
            box(.05, .61, .07, (.305, 0, .095), dark),              # front frame rail
            box(.56, .05, .07, (0, -.28, .095), dark),              # left rail, between them
            box(.56, .05, .07, (0, .28, .095), dark),               # right rail
            box(.20, .22, .08, (-.02, 0, .20), dark),               # turret pedestal housing
            cylinder(.11, .03, "z", (-.02, 0, .255), gray),         # turret bearing collar
            cylinder(.012, .49, "y", (hinge_x, 0, hinge_z), gray)]  # hinge shaft, boss to boss
    # Elevator structure is tracked separately: the turret must clear it by a real margin, while
    # seated joints elsewhere on the chassis are allowed to touch.
    elevator_static = [box(.05, .46, .05, (elevator_x, 0, crossbar_z), gray)]
    upright_top = crossbar_z - .025
    for sign in (-1, 1):
        base.append(box(.04, .86, .10, (sign * .41, 0, .15), bumper))               # bumpers
        base.append(box(.78, .04, .10, (0, sign * .40, .15), bumper))
        elevator_static.append(box(.05, .05, upright_top - .16,
                                   (elevator_x, sign * upright_y, (upright_top + .16) / 2), dark))
        base.append(box(.06, .03, .10, (hinge_x, sign * .26, .21), dark))           # hinge bosses
        base.append(box(.06, .025, .10, (.23, sign * .16, .21), dark))              # feeder bearings
    base.extend(elevator_static)
    models = [base,
              striped(.04, .293, "y", origin, ["#EB8936", "#B5621C"]),  # 1 mm off the bearings
              [box(.07, .24, .12, origin, "#5B8FF9"),
               box(.07, .04, .12, (0, -.14, 0), "#3B6FD4"),
               box(.07, .04, .12, (0, .14, 0), "#3B6FD4"),
               box(.07, .24, .02, (0, 0, .07), "#5B8FF9")],
              # Intake frame: rails from the hinge tube's surface out past the roller, crossbars
              # between the rails, roller tabs outside them.
              [box(.315, .03, .035, (.1825, -.225, 0), "#3FB950"),
               box(.315, .03, .035, (.1825, .225, 0), "#3FB950"),
               box(.03, .42, .03, (.055, 0, 0), "#2EA043"),
               box(.03, .42, .03, (.26, 0, 0), "#2EA043"),          # clear of the roller
               box(.10, .025, .05, (.28, -.2525, 0), "#2EA043"),
               box(.10, .025, .05, (.28, .2525, 0), "#2EA043"),
               cylinder(.025, .47, "y", origin, "#2EA043")],
              striped(.035, .418, "y", origin, ["#1F9E59", "#0F5330"]),  # 1 mm off the rails
              # Turret: a low disc on the bearing collar, with cheeks rising to the hood's pivot
              # so the barrel clears the disc at full depression, and a tab at the front.
              [cylinder(.115, .02, "z", (0, 0, -.02), "#D2A8FF"),
               box(.08, .025, .13, (0, -.07, .055), "#B388EB"),
               box(.08, .025, .13, (0, .07, .055), "#B388EB"),
               box(.04, .04, .02, (.135, 0, -.02), "#9B6BDF")],
              # Hood: a body on the trunnion, the barrel from its front face, a muzzle ring, and
              # a sight rib resting on the barrel.
              [box(.10, .11, .08, (.005, 0, 0), "#F778BA"),
               cylinder(.05, .095, "x", (.1025, 0, 0), "#F778BA"),
               cylinder(.055, .03, "x", (.165, 0, 0), "#DB61A2"),
               box(.095, .022, .014, (.1025, 0, .057), "#DB61A2")]]
    # Swerve modules, front-left, front-right, back-left, back-right -- the order of
    # RobotPhysics.Drive.MODULE_LOCATIONS. Pods pivot at the module's floor point; wheels pivot
    # at their axle, which rides the pod at wheel_z.
    module_xy = [(.14, .195), (.14, -.195), (-.14, .195), (-.14, -.195)]
    wheel_r, wheel_w = .0508, .038
    pod = [box(.08, .008, .08, (0, -.027, .09), "#8B949E"),         # fork plates
           box(.08, .008, .08, (0, .027, .09), "#8B949E"),
           cylinder(.03, .03, "z", (0, 0, .175), "#F2CC60"),        # steer cap above the deck
           box(.045, .014, .012, (.0225, 0, .196), "#9E6A03")]      # pointer: rolling direction
    wheel = striped(wheel_r, wheel_w, "y", origin, [tire, "#5A636C"])  # stripes show the spin
    models += [pod] * 4 + [wheel] * 4
    # Clearances the MockBot poses rely on. The turret yaws without limit about x = -0.02, so the
    # hood sweeps a full disc: every static part, and the carriage anywhere in its travel, must
    # stay outside it. Computed from the real vertices over the real pitch range, not from a
    # hand-derived radius -- an approximate radius is how a barrel ends up inside the elevator.
    # models[0] is the static base; the rest follow config.json component order.
    feeder, carriage, intake_frame, intake_roller, turret, hood = models[1:7]
    turret_xy, turret_z, hood_lift = (-.02, 0), .30, .09
    pitch_limit = math.radians(15)
    obstacles = [(f"base part {i}", bounds(p.positions), 0.0) for i, p in enumerate(base)]
    obstacles += [(f"elevator part {i}", bounds(p.positions), .005)
                  for i, p in enumerate(elevator_static)]
    for i, p in enumerate(carriage):
        lo, hi = bounds(p.positions)
        lo[0] += elevator_x
        hi[0] += elevator_x
        lo[2] += carriage_z0
        hi[2] += carriage_z0 + carriage_travel
        obstacles.append((f"carriage part {i}", (lo, hi), .005))
    for label, parts, lift, limit in (("turret", turret, 0, 0.0),
                                      ("hood", hood, hood_lift, pitch_limit)):
        hull = [(r, z + turret_z + lift) for r, z in profile(parts, limit)]
        worst = None
        for name, box_bounds, margin in obstacles:
            gap = separation(hull, obstacle_profile(box_bounds, turret_xy))
            if margin and (worst is None or gap < worst[0]):
                worst = (gap, name)
            assert gap >= margin - 1e-9, (
                f"{label} can touch {name}: profile overlap {-gap * 1000:.1f} mm")
        print(f"{label}: swept profile clears every obstacle, worst margin "
              f"{worst[0] * 1000:.1f} mm to {worst[1]}")
    # The hood pitches over the turret's disc and front block: at every pitch, every hood vertex
    # above one of them (by its bounds, a superset of a cylinder's footprint) must stay over its top.
    for i in range(17):
        pitch = pitch_limit * (2 * i / 16 - 1)
        c, s = math.cos(pitch), math.sin(pitch)
        for part in hood:
            for x, y, z in part.positions:
                hx, hz = x * c + z * s, -x * s + z * c + hood_lift
                for under in (turret[0], turret[3]):
                    lo, hi = bounds(under.positions)
                    if lo[0] <= hx <= hi[0] and lo[1] <= y <= hi[1]:
                        assert hz >= hi[2] + .003, (
                            f"hood dips into the turret at pitch {pitch:.3f}: "
                            f"{(hi[2] - hz) * 1000:.1f} mm")
    # Every module steers without limit, so each pod and wheel sweeps a disc about its vertical
    # axis. Each swept disc must clear every static part, the carriage over its travel and the
    # feeder roller (a cylinder, so its bounds hold at any spin); seated faces may touch.
    feeder_bounds = bounds([p for part in feeder for p in part.positions])
    feeder_bounds = ([feeder_bounds[0][0] + .23, feeder_bounds[0][1], feeder_bounds[0][2] + .21],
                     [feeder_bounds[1][0] + .23, feeder_bounds[1][1], feeder_bounds[1][2] + .21])
    module_obstacles = obstacles + [("feeder roller", feeder_bounds, 0.0)]
    worst = None
    for mx, my in module_xy:
        for label, parts, lift in (("pod", pod, 0.0), ("wheel", wheel, wheel_r)):
            for part in parts:
                radius = max(math.hypot(x, y) for x, y, _ in part.positions)
                zlo = min(z for *_, z in part.positions) + lift
                zhi = max(z for *_, z in part.positions) + lift
                for name, ((lox, loy, loz), (hix, hiy, hiz)), _ in module_obstacles:
                    if min(zhi, hiz) - max(zlo, loz) <= 1e-9:
                        continue  # stacked or seated, not overlapping in height
                    gap = math.hypot(max(lox - mx, 0.0, mx - hix),
                                     max(loy - my, 0.0, my - hiy)) - radius
                    assert gap >= -1e-9, (f"module at {mx, my}: {label} sweeps into {name}, "
                                          f"overlap {-gap * 1000:.1f} mm")
                    if worst is None or gap < worst[0]:
                        worst = (gap, f"{label} at {mx, my} to {name}")
    print(f"swerve: every module sweeps clear, worst margin {worst[0] * 1000:.1f} mm "
          f"({worst[1]})")
    assert crossbar_z - .025 - (carriage_z0 + carriage_travel + .08) >= .02
    assert upright_y - .025 - (carriage_half_y + .02) >= .01
    for i, parts in enumerate(models):
        check_static(parts, "model.glb" if i == 0 else f"model_{i - 1}.glb")
    config = {"name": "Block Bot", "isFTC": False, "disableSimplification": True,
              "rotations": [], "position": [0, 0, 0], "cameras": [],
              "components": [{"zeroedRotations": [], "zeroedPosition": [0, 0, 0]}
                             for _ in range(len(models) - 1)]}
    (output / "config.json").write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    for i, parts in enumerate(models):
        name = "model" if i == 0 else f"model_{i - 1}"
        path = output / (name + ".glb")
        path.write_bytes(glb(parts, name))
        lo, hi = bounds([p for part in parts for p in part.positions])
        validate(path, expect_x=(lo[0], hi[0]))
    assert len(list(output.glob("model*.glb"))) == 15
    assert len(json.loads((output / "config.json").read_text())["components"]) == 14
    install(output)


def install(output):
    """Mirror the generated set into AdvantageScope's user assets.

    The viewer reads only this folder, so a repo-only regenerate silently leaves it drawing the
    previous geometry against the new poses -- parts then interpenetrate or float off their
    pivots. Writing every file on every run keeps the two from drifting apart.
    """
    appdata = os.environ.get("APPDATA")
    if not appdata:
        return
    target = Path(appdata) / "AdvantageScope" / "userAssets" / output.name
    target.mkdir(parents=True, exist_ok=True)
    for path in output.iterdir():
        if path.suffix in (".glb", ".json"):
            shutil.copy2(path, target / path.name)
    print(f"installed {len(list(target.glob('model*.glb')))} models + config -> {target}")
    print("restart AdvantageScope to pick up new geometry (models are cached per session)")


if __name__ == "__main__":
    main()
