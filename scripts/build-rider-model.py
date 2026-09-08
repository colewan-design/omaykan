"""
Puts a rider, a uniform and a delivery box onto the bare scooter model.

`scooter.glb` is a bought motorbike and nothing else — no rider, no box, no
livery. On a map that reads as a vehicle rather than as a delivery, and the
whole point of the marker is that a customer watching it knows what it is. This
script is what turns the one into the other.

    python -m pip install numpy scikit-image pillow
    python scripts/build-rider-model.py

Run from anywhere. Reads the bare bike from

    scripts/rider-model/scooter.glb

and writes the dressed one to **two** places, because two apps draw the same
marker and both of them ship it:

    apps/mobile-android/rider/src/main/assets/scooter_rider.glb    (Android)
    apps/web/public/delivery/rider.glb                             (web)

which is why this lives in `scripts/` rather than under either app. One marker,
one generator, no chance of the customer's map and the rider's map disagreeing
about what a delivery looks like.

The bare bike deliberately sits *here* and not in either app's assets. It is an
input, not a deliverable: parked in `src/main/assets` it would be 600 KB of
undressed motorbike shipped to every rider for no reason. The input is never
modified, so a bad run costs nothing and the bike can always be re-dressed.

    --preview DIR   also write side/front/top renders of the result as PNGs
    --cell N        distance-field resolution; the size/detail dial

The three dependencies are needed only to *change* the model. Both outputs are
committed, so a checkout that just wants to build either app needs none of them.

## Why the resolution is what it is

The default cell size is a web decision, not an Android one. The Android copy
is a debug asset and could be any size; the web copy is downloaded by every
customer watching a delivery, over a connection this repo's own map loader
worries about out loud. At 0.17 the model is 11k triangles and 880 KB, ~480 KB
over the wire once gzipped, against 1.07 MB at the resolution the eye can
actually tell apart from it. The finer one is not worth 200 KB on a DSL line.

## Why the rider is a distance field and not a pile of primitives

The first version of this built the rider out of separate tubes and spheres —
an arm here, a torso there — and it looked exactly like that: a snowman. The
tell is the seams. Where a real shoulder is a continuous surface flowing into
an arm, glued primitives meet at a hard silhouette crease, and at any zoom
where the rider is more than a dozen pixels tall the eye reads it instantly.

So the body is defined as a *field* instead: every limb contributes a signed
distance, they are combined with a smooth minimum, and the surface is whatever
comes out at distance zero. Limbs merge into the torso the way flesh does,
because a smooth minimum is doing the same job a fillet does. Marching cubes
turns that field into triangles and the normals come from the field's gradient,
so the shading is smooth without a single explicit seam.

It costs about 12k triangles, which is nothing next to the 16k the bike already
spends, and it is why this is worth the three build-time dependencies.

## Why generated and not modelled

A .glb is an opaque binary. Committed on its own, "why is the rider's arm at
that angle" is a question with no answer short of opening Blender, and the next
person to want the box larger has to have Blender, have the source .blend, and
hope it matches what shipped. The skeleton below is a table of numbers that can
be read, diffed and reverted.

It also has to line up with a bike this file did not author. Every landmark —
the grips, the seat, the tank, the tail cowl — was measured off the bike's own
vertices rather than guessed, which is why the hands land *on* the bars.

## The coordinate system, which is not the obvious one

The bike's nine nodes all carry the same transform: a 180° rotation about
(0, -0.7015, 0.7127), a uniform scale of 0.14560, and a translation. The
rider's nodes are given that same transform, so everything here is authored in
the bike's *local* space and lands wherever the bike lands.

In that space, and this is worth reading twice:

    +x  right            (lateral, the bike is symmetric about x = 0)
    -y  FORWARD          (the headlight is at y = -8.5, the tail at y = +4.9)
    -z  UP               (the tyres touch the ground at z = +1.73)

so a bigger z is *lower*, and the nose of the bike is at negative y. One local
unit is 0.1456 m, which makes the bike 2.18 m long and 1.23 m tall.
"""

from __future__ import print_function

import argparse
import json
import math
import os
import struct
import sys

try:
    import numpy as np
    from skimage import measure
except ImportError:  # pragma: no cover - the message is the whole point
    raise SystemExit(
        'this script needs numpy and scikit-image:\n'
        '    python -m pip install numpy scikit-image pillow\n'
        'The generated scooter_rider.glb is committed, so only changing the '
        'model needs them.'
    )


# --- the bike, as measured off its own vertices -----------------------------
#
# Every one of these came out of the POSITION accessors of scooter.glb rather
# than out of a guess, which is why the rider's hands land on the grips instead
# of near them.

GROUND_Z = 1.73                # lowest point of the tyres
SEAT_Z = -4.85                 # top of the seat pad, around y = -2.9
GRIP = (2.40, -5.36, -5.52)    # right-hand grip; the left is its mirror
TAIL_Z = -5.25                 # top of the rear cowl, y = -0.5 .. +1.0
UNIT_M = 0.14560               # one local unit, in metres


# --- the rider, as a skeleton ------------------------------------------------
#
# Sportbike posture, because that is the bike: the grips sit only 0.7 units
# above the seat, so a rider who reaches them is leaning. The torso is 20° off
# vertical — enough to read as riding rather than as sitting on a bench, not so
# far that the model becomes a wedge when seen from above, which is the angle
# it is actually looked at from.
#
# Segment lengths were checked against a 1.75 m person: femur 45 cm, tibia
# 43 cm, torso 54 cm, arm 60 cm to the grip. They come out slightly small
# against this particular bike, whose seat sits at 0.96 m — the model is a big
# one — and that is the right way round. A rider scaled to the bike would be a
# giant; a rider scaled to a human just makes the bike look like a litre bike.

HIP = (0.86, -2.70, -5.45)          # hip joint, right side
PELVIS_Z = -5.52
KNEE = (1.38, -4.62, -3.15)         # knees grip the tank, which is ±0.87 wide
ANKLE = (1.45, -2.45, -1.15)
TOE = (1.45, -3.32, -1.00)

WAIST = (0.0, -3.05, -6.85)
CHEST = (0.0, -3.60, -8.05)
SHOULDER_C = (0.0, -3.90, -8.62)    # deltoid centre, NOT the acromion
SHOULDER_X = 1.16
ELBOW = (2.02, -4.34, -6.98)
WRIST = (2.38, -5.22, -5.80)
HAND = (2.44, -5.38, -5.50)

NECK_TOP = (0.0, -4.12, -9.38)
HEAD = (0.0, -4.30, -10.30)
HELMET_R = (0.98, 1.06, 1.02)


# --- the top box -------------------------------------------------------------

BOX_Y = 0.90
BOX_HALF = (1.75, 1.70, 1.65)
BOX_FLOOR_Z = TAIL_Z - 0.25


def srgb(hex_colour):
    """
    A CSS hex to a glTF baseColorFactor.

    glTF colour factors are *linear*, and every colour in this app's palette is
    written the way CSS writes it, which is sRGB. Handing the sRGB value
    straight to a renderer is the single commonest way a brand green arrives on
    screen looking like a highlighter — visibly, roughly two stops too bright.
    """
    h = hex_colour.lstrip('#')
    out = []
    for i in (0, 2, 4):
        c = int(h[i:i + 2], 16) / 255.0
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return out + [1.0]


# The uniform, in the app's own colours — RiderTheme's accent green and canopy
# near-black, not a green picked to look nice here. A marker in the product's
# palette is the product; a marker in some other green is a sticker on it.
#
# There is deliberately no skin tone anywhere: full-face helmet, gloves, long
# sleeves. That is honest for a rider in the rain on Kennon Road, and it side-
# steps picking one skin colour to stand for every rider on the platform.
MATERIALS = [
    ('Rider_Uniform', srgb('#1A6B3C'), 0.0, 0.62),   # jacket, sleeves, helmet
    ('Rider_Trouser', srgb('#1E2428'), 0.0, 0.70),   # legs
    ('Rider_Dark', srgb('#0F1214'), 0.1, 0.50),      # gloves, boots
    ('Rider_HiVis', srgb('#92DD73'), 0.0, 0.48),     # yoke and box panels
    ('Rider_Visor', srgb('#0B1114'), 0.35, 0.08),    # the one glossy part
    ('Rider_Box', srgb('#0E2B1B'), 0.0, 0.70),       # the top box, canopy green
]
UNIFORM, TROUSER, DARK, HIVIS, VISOR, BOX = range(len(MATERIALS))

PREVIEW_RGB = {
    UNIFORM: (26, 107, 60),
    TROUSER: (30, 36, 40),
    DARK: (15, 18, 20),
    HIVIS: (146, 221, 115),
    VISOR: (11, 17, 20),
    BOX: (14, 43, 27),
}


def mirrored(p):
    return (-p[0], p[1], p[2])


# --- the distance field ------------------------------------------------------

class Capsule(object):
    """
    A tapered capsule with an elliptical cross-section — every limb and the
    whole torso.

    ## Why [scale] exists

    A circular capsule makes a barrel, and the first version of this was built
    entirely out of them: seen head-on the rider was as deep as he was wide,
    which is a beer keg, not a chest. A person is roughly 35 cm across and
    23 cm front-to-back, and nothing else about the model matters until that
    ratio is right.

    So the distance is measured in a squashed space — divide by [scale], solve
    the round case, and scale the answer back by the smallest axis so the
    result stays a conservative distance and the smooth minimum keeps behaving.
    [ra]/[rb] are radii in that squashed space, so the real half-width across
    the bike is `ra * scale[0]`.

    Not an exact field where the two radii differ (the true round cone has a
    conical section this ignores), and it does not matter: the error is a
    fraction of the blend width and everything here is smoothed together
    afterwards anyway.
    """

    def __init__(self, a, b, ra, rb, material, scale=(1.0, 1.0, 1.0)):
        self.s = np.array(scale, dtype=np.float32)
        self.a = np.array(a, dtype=np.float32) / self.s
        self.b = np.array(b, dtype=np.float32) / self.s
        self.ra = float(ra)
        self.rb = float(rb)
        self.material = material

    def distance(self, p):
        q = p / self.s
        ab = self.b - self.a
        ap = q - self.a
        denom = float(np.dot(ab, ab))
        t = np.clip(ap.dot(ab) / denom, 0.0, 1.0) if denom > 1e-9 else np.zeros(len(q), np.float32)
        closest = self.a + t[:, None] * ab
        d = np.linalg.norm(q - closest, axis=1) - (self.ra + (self.rb - self.ra) * t)
        return d * float(self.s.min())

    def bounds(self):
        reach = max(self.ra, self.rb) * self.s
        a, b = self.a * self.s, self.b * self.s
        return np.minimum(a, b) - reach, np.maximum(a, b) + reach


class Ellipsoid(object):
    """The skull, and the shell over it. The standard inexact-but-stable form."""

    def __init__(self, centre, radii, material):
        self.c = np.array(centre, dtype=np.float32)
        self.r = np.array(radii, dtype=np.float32)
        self.material = material

    def distance(self, p):
        q = (p - self.c) / self.r
        k0 = np.linalg.norm(q, axis=1)
        k1 = np.linalg.norm(q / self.r, axis=1)
        return np.where(k1 > 1e-9, k0 * (k0 - 1.0) / np.maximum(k1, 1e-9), k0 - 1.0)

    def bounds(self):
        return self.c - self.r, self.c + self.r


def smooth_min(parts, p, k):
    """
    The exponential smooth minimum, which is what makes a shoulder a shoulder.

    Shifted by the true minimum before exponentiating. Without that shift a
    point deep inside the torso has a large negative distance, exp() of it
    overflows to inf, and the whole field turns into NaN — which shows up as a
    model with no surface at all and takes an embarrassingly long time to
    diagnose.
    """
    best = None
    for part in parts:
        d = part.distance(p)
        best = d if best is None else np.minimum(best, d)

    total = np.zeros(len(p), dtype=np.float64)
    for part in parts:
        total += np.exp(-k * (part.distance(p) - best))

    return (best - np.log(total) / k).astype(np.float32)


def nearest_material(parts, p):
    best = None
    which = np.zeros(len(p), dtype=np.int32)
    for i, part in enumerate(parts):
        d = part.distance(p)
        if best is None:
            best = d
        else:
            closer = d < best
            best = np.where(closer, d, best)
            which = np.where(closer, i, which)
    return np.array([parts[i].material for i in range(len(parts))], dtype=np.int32)[which]


def surface(parts, low, high, cell, k):
    """
    The field, marched into triangles.

    Normals come from the gradient of the field rather than from skimage, for
    two reasons: the gradient of a signed distance function is the outward
    normal by definition, so there is no orientation to guess; and it is the
    *smooth* field, so the shading follows the blended surface instead of the
    faceting of the grid it was sampled on.
    """
    low = np.array(low, dtype=np.float32) - cell * 2
    high = np.array(high, dtype=np.float32) + cell * 2
    dims = np.maximum(np.ceil((high - low) / cell).astype(int), 2)

    axes = [low[i] + np.arange(dims[i], dtype=np.float32) * cell for i in range(3)]
    grid = np.stack(np.meshgrid(*axes, indexing='ij'), axis=-1).reshape(-1, 3)

    volume = smooth_min(parts, grid, k).reshape(dims)
    if volume.min() > 0 or volume.max() < 0:
        raise SystemExit('the field never crosses zero — check the bounds')

    verts, faces, _, _ = measure.marching_cubes(volume, level=0.0, spacing=(cell, cell, cell))
    verts = (verts + low).astype(np.float32)

    # Central differences on the smooth field. One evaluation per axis per
    # direction: six passes over a few thousand points, which is free.
    eps = cell * 0.5
    normals = np.zeros_like(verts)
    for axis in range(3):
        step = np.zeros(3, dtype=np.float32)
        step[axis] = eps
        normals[:, axis] = smooth_min(parts, verts + step, k) - smooth_min(parts, verts - step, k)
    lengths = np.linalg.norm(normals, axis=1, keepdims=True)
    normals /= np.maximum(lengths, 1e-9)

    # Wind every triangle so its geometric normal agrees with the field's.
    # Cheaper than reasoning about how skimage orders its output, and correct
    # whichever way that happens to be.
    a, b, c = verts[faces[:, 0]], verts[faces[:, 1]], verts[faces[:, 2]]
    geometric = np.cross(b - a, c - a)
    inward = (geometric * normals[faces[:, 0]]).sum(axis=1) < 0
    faces[inward] = faces[inward][:, ::-1]

    centroids = (a + b + c) / 3.0
    return verts, normals, faces, nearest_material(parts, centroids)


# --- the rider ---------------------------------------------------------------

def rider_parts():
    """
    The body, as a list of limbs.

    Ordering matters only for `nearest_material`: where two parts overlap, the
    nearer surface wins the colour, which is why the glove is a separate short
    capsule at the end of the forearm rather than a colour applied to a range
    of the arm.
    """
    # Torso cross-sections are wide and shallow; limbs are very nearly round.
    TRUNK = (1.22, 0.86, 1.0)
    LIMB = (1.0, 0.92, 1.0)

    parts = [
        # Pelvis and the spine up to the shoulders. Three segments rather than
        # one so the back curves; a straight torso reads as a mannequin.
        Capsule((-0.44, -2.66, PELVIS_Z), (0.44, -2.66, PELVIS_Z), 0.72, 0.72,
                TROUSER, (1.20, 0.95, 1.0)),
        Capsule((0.0, -2.78, -5.95), WAIST, 0.80, 0.84, UNIFORM, TRUNK),
        Capsule(WAIST, CHEST, 0.84, 0.96, UNIFORM, TRUNK),

        # The shoulder line, and the hi-vis yoke is this same bar: the shoulders
        # ARE the high-visibility panel on a delivery jacket, so one capsule
        # does both jobs and there is no stripe floating above the cloth.
        #
        # Centred on the deltoid rather than on the joint. Put at the joint —
        # which is the obvious reading of "shoulder height" — its radius adds
        # 10 cm of muscle *above* the shoulder line, the helmet ends up sunk
        # between two humps, and the rider has no neck at all.
        Capsule((-SHOULDER_X, SHOULDER_C[1], SHOULDER_C[2]),
                (SHOULDER_X, SHOULDER_C[1], SHOULDER_C[2]), 0.70, 0.70,
                HIVIS, (1.0, 0.88, 1.0)),

        Capsule((0.0, -4.00, -8.95), NECK_TOP, 0.42, 0.40, DARK),
    ]

    for side in (1, -1):
        def s(p):
            return (p[0] * side, p[1], p[2])

        shoulder = s((SHOULDER_X * 0.94, SHOULDER_C[1] - 0.04, SHOULDER_C[2] + 0.06))
        parts += [
            Capsule(shoulder, s(ELBOW), 0.42, 0.34, UNIFORM, LIMB),
            Capsule(s(ELBOW), s(WRIST), 0.34, 0.27, UNIFORM, LIMB),
            Capsule(s(WRIST), s(HAND), 0.28, 0.31, DARK),

            Capsule(s(HIP), s(KNEE), 0.62, 0.45, TROUSER, LIMB),
            Capsule(s(KNEE), s(ANKLE), 0.43, 0.31, TROUSER, LIMB),
            Capsule(s(ANKLE), s(TOE), 0.34, 0.28, DARK, (1.0, 1.0, 0.82)),
        ]

    return parts


def helmet_parts():
    """
    A full-face helmet: shell, chin bar, and the visor painted on afterwards.

    Marched separately from the body on purpose. Blended into the shoulders it
    would grow a neck-shaped fillet and stop looking like a removable object,
    which a helmet very much is.
    """
    return [
        Ellipsoid(HEAD, HELMET_R, UNIFORM),
        # Chin bar, across the front and low. Three capsules: one across, one
        # each side sweeping back up into the shell.
        Capsule((-0.56, -5.00, -9.80), (0.56, -5.00, -9.80), 0.38, 0.38, UNIFORM),
        Capsule((-0.56, -5.00, -9.80), (-0.84, -4.45, -10.25), 0.38, 0.46, UNIFORM),
        Capsule((0.56, -5.00, -9.80), (0.84, -4.45, -10.25), 0.38, 0.46, UNIFORM),
        # A small rear spoiler, which is most of what says "helmet" in
        # silhouette from behind — the angle this model is most often seen at.
        Capsule((0.0, -3.60, -10.72), (0.0, -3.24, -10.58), 0.36, 0.22, UNIFORM),
    ]


def paint_visor(verts, materials, faces):
    """
    Recolour the helmet's eye band, rather than modelling an aperture.

    A boolean cut through the shell would be a lot of triangles spent on a
    feature four pixels wide. What actually reads at map scale is the dark band
    itself, and a band is a material, not a hole.
    """
    centre = np.array([0.0, -4.95, -10.48], dtype=np.float32)
    radii = np.array([1.00, 0.82, 0.42], dtype=np.float32)

    tri = verts[faces].mean(axis=1)
    inside = np.linalg.norm((tri - centre) / radii, axis=1) < 1.0
    # Front half only: without this the band wraps round the back of the head.
    inside &= tri[:, 1] < HEAD[1] - 0.15

    painted = materials.copy()
    painted[inside] = VISOR
    return painted


# --- the box, which is a box -------------------------------------------------

class Slabs(object):
    """
    Flat-shaded boxes, kept well away from the distance field.

    The box wants hard edges and a crisp lid seam; run through a smooth minimum
    it would come out as a bar of soap. Two different shapes, two different
    methods, and that is the right answer rather than a missing abstraction.
    """

    def __init__(self):
        self.groups = {}

    def box(self, material, centre, half):
        pos, nrm, idx = self.groups.setdefault(material, ([], [], []))
        cx, cy, cz = centre
        hx, hy, hz = half
        corners = [(cx + sx * hx, cy + sy * hy, cz + sz * hz)
                   for sx, sy, sz in ((-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
                                      (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1))]
        for a, b, c, d in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4),
                           (2, 3, 7, 6), (1, 2, 6, 5), (3, 0, 4, 7)):
            p, q, r, s = corners[a], corners[b], corners[c], corners[d]
            u = (q[0] - p[0], q[1] - p[1], q[2] - p[2])
            v = (r[0] - p[0], r[1] - p[1], r[2] - p[2])
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            length = math.sqrt(sum(c * c for c in n)) or 1.0
            n = tuple(c / length for c in n)
            base = len(pos)
            pos.extend([p, q, r, s])
            nrm.extend([n, n, n, n])
            idx.extend([base, base + 1, base + 2, base, base + 2, base + 3])


def build_box(slabs):
    """
    The top box, on a rack, panelled on every face a map camera can see.

    ## The lid is the face that matters

    The obvious build — green body, dark lid — is wrong here, and looked it:
    from a 55° camera pitch the lid is most of the box's visible area, so a
    dark lid turns the whole thing into a black slab and the green body is a
    sliver nobody reads. The lid carries the livery and the panel on top of it
    is the brand mark.

    The box is canopy green rather than the jacket's accent green, and the
    difference is deliberate: the same green on both makes rider and box one
    continuous blob at the size this is actually drawn. Two greens with a dark
    rack between them is what separates "a person" from "a box".
    """
    hx, hy, hz = BOX_HALF
    centre_z = BOX_FLOOR_Z - hz
    top_z = centre_z - hz

    slabs.box(DARK, (0.0, BOX_Y, BOX_FLOOR_Z + 0.14), (hx * 0.88, hy * 0.95, 0.16))
    slabs.box(BOX, (0.0, BOX_Y, centre_z), (hx, hy, hz))
    slabs.box(BOX, (0.0, BOX_Y, top_z - 0.10), (hx + 0.07, hy + 0.07, 0.15))

    # Panels: the lid, both flanks, and the back. Not the front — nobody sees
    # the face pressed against the rider, and it would z-fight with the jacket.
    slabs.box(HIVIS, (0.0, BOX_Y, top_z - 0.26), (hx * 0.62, hy * 0.62, 0.05))
    panel_z = centre_z + 0.10
    for side in (1, -1):
        slabs.box(HIVIS, (side * (hx + 0.05), BOX_Y, panel_z), (0.06, hy * 0.66, hz * 0.58))
    slabs.box(HIVIS, (0.0, BOX_Y + hy + 0.05, panel_z), (hx * 0.66, 0.06, hz * 0.58))


# --- glb assembly ------------------------------------------------------------

def load_glb(path):
    data = open(path, 'rb').read()
    if data[:4] != b'glTF':
        raise SystemExit('%s is not a glb' % path)
    offset, js, binary = 12, None, b''
    while offset < len(data):
        length, kind = struct.unpack('<II', data[offset:offset + 8])
        chunk = data[offset + 8:offset + 8 + length]
        if kind == 0x4E4F534A:
            js = json.loads(chunk)
        elif kind == 0x004E4942:
            binary = chunk
        offset += 8 + length
    return js, bytearray(binary)


def save_glb(path, js, binary):
    text = json.dumps(js, separators=(',', ':')).encode('utf-8')
    text += b' ' * ((4 - len(text) % 4) % 4)
    blob = bytes(binary)
    blob += b'\x00' * ((4 - len(blob) % 4) % 4)

    out = bytearray(b'glTF')
    out += struct.pack('<II', 2, 12 + 8 + len(text) + 8 + len(blob))
    out += struct.pack('<II', len(text), 0x4E4F534A) + text
    out += struct.pack('<II', len(blob), 0x004E4942) + blob
    open(path, 'wb').write(bytes(out))
    return len(out)


def append_view(js, binary, payload, target):
    while len(binary) % 4:
        binary.append(0)
    view = {'buffer': 0, 'byteOffset': len(binary), 'byteLength': len(payload)}
    if target:
        view['target'] = target
    binary.extend(payload)
    js['bufferViews'].append(view)
    return len(js['bufferViews']) - 1


def merge(js, binary, groups, transform):
    """Each material group becomes one mesh, one node, one entry in the scene."""
    material_base = len(js['materials'])
    for name, colour, metallic, roughness in MATERIALS:
        js['materials'].append({
            'name': name,
            'doubleSided': True,
            'pbrMetallicRoughness': {
                'baseColorFactor': colour,
                'metallicFactor': metallic,
                'roughnessFactor': roughness,
            },
        })

    triangles = 0
    for material in sorted(groups):
        positions, normals, indices = groups[material]
        pos_view = append_view(js, binary, positions.astype('<f4').tobytes(), 34962)
        nrm_view = append_view(js, binary, normals.astype('<f4').tobytes(), 34962)
        idx_view = append_view(js, binary, indices.astype('<u4').tobytes(), 34963)

        first = len(js['accessors'])
        js['accessors'].append({'bufferView': pos_view, 'componentType': 5126,
                                'count': len(positions), 'type': 'VEC3',
                                'min': [float(v) for v in positions.min(axis=0)],
                                'max': [float(v) for v in positions.max(axis=0)]})
        js['accessors'].append({'bufferView': nrm_view, 'componentType': 5126,
                                'count': len(normals), 'type': 'VEC3'})
        js['accessors'].append({'bufferView': idx_view, 'componentType': 5125,
                                'count': len(indices), 'type': 'SCALAR'})

        name = MATERIALS[material][0]
        js['meshes'].append({
            'name': name,
            'primitives': [{
                'attributes': {'POSITION': first, 'NORMAL': first + 1},
                'indices': first + 2,
                'material': material_base + material,
            }],
        })

        node = dict(transform)
        node['name'] = name
        node['mesh'] = len(js['meshes']) - 1
        js['nodes'].append(node)
        js['scenes'][js.get('scene', 0)]['nodes'].append(len(js['nodes']) - 1)
        triangles += len(indices) // 3

    js['buffers'][0]['byteLength'] = len(binary)
    return triangles


def split_by_material(verts, normals, faces, materials, groups):
    """Re-index each material's triangles into its own vertex array."""
    for material in np.unique(materials):
        picked = faces[materials == material]
        used, remapped = np.unique(picked, return_inverse=True)
        add_group(groups, int(material), verts[used], normals[used],
                  remapped.astype(np.uint32))


def add_group(groups, material, positions, normals, indices):
    positions = np.asarray(positions, dtype=np.float32).reshape(-1, 3)
    normals = np.asarray(normals, dtype=np.float32).reshape(-1, 3)
    indices = np.asarray(indices, dtype=np.uint32).reshape(-1)

    if material in groups:
        have_pos, have_nrm, have_idx = groups[material]
        indices = indices + len(have_pos)
        positions = np.vstack([have_pos, positions])
        normals = np.vstack([have_nrm, normals])
        indices = np.concatenate([have_idx, indices])

    groups[material] = (positions, normals, indices)


# --- preview -----------------------------------------------------------------

def render(groups, path, view, size=760):
    """
    A flat-shaded orthographic render, so anatomy can be judged without a
    build, an install and a screenshot for every tweak to a radius.

    A painter's z-buffer over a few thousand triangles. Crude, and it answers
    the only question being asked of it: does this look like a person.
    """
    from PIL import Image

    axis = {'side': (1, 2, 0), 'front': (0, 2, 1), 'top': (0, 1, 2)}[view]
    # No flip: image rows increase downward and so does local z, because -z is
    # up in this space. The two cancel, and negating here — which is the
    # obvious thing to write — renders the bike upside down.
    flip = 1.0

    tris, colours = [], []
    for material, (positions, normals, indices) in groups.items():
        faces = indices.reshape(-1, 3)
        tris.append(positions[faces])
        shade = np.abs(normals[faces].mean(axis=1)[:, 2]) * 0.55 + 0.45
        base = np.array(PREVIEW_RGB[material], dtype=np.float32)
        colours.append(np.clip(shade[:, None] * base, 0, 255))
    tris = np.concatenate(tris)
    colours = np.concatenate(colours)

    screen = tris[:, :, [axis[0], axis[1]]] * np.array([1.0, flip], dtype=np.float32)
    depth = tris[:, :, axis[2]].mean(axis=1)

    low = screen.reshape(-1, 2).min(axis=0)
    high = screen.reshape(-1, 2).max(axis=0)
    scale = (size - 40) / max(high - low)
    screen = (screen - low) * scale + 20
    height = int((high[1] - low[1]) * scale) + 40
    width = int((high[0] - low[0]) * scale) + 40

    image = np.full((height, width, 3), 246, dtype=np.float32)
    zbuf = np.full((height, width), 1e9, dtype=np.float32)

    order = np.argsort(-depth)
    for i in order:
        (x0, y0), (x1, y1), (x2, y2) = screen[i]
        lo_x, hi_x = int(max(min(x0, x1, x2), 0)), int(min(max(x0, x1, x2) + 1, width))
        lo_y, hi_y = int(max(min(y0, y1, y2), 0)), int(min(max(y0, y1, y2) + 1, height))
        if lo_x >= hi_x or lo_y >= hi_y:
            continue
        yy, xx = np.mgrid[lo_y:hi_y, lo_x:hi_x]
        xx = xx + 0.5
        yy = yy + 0.5
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        w0 = ((x1 - xx) * (y2 - yy) - (x2 - xx) * (y1 - yy)) / area
        w1 = ((x2 - xx) * (y0 - yy) - (x0 - xx) * (y2 - yy)) / area
        w2 = 1.0 - w0 - w1
        hit = (w0 >= 0) & (w1 >= 0) & (w2 >= 0) & (depth[i] < zbuf[lo_y:hi_y, lo_x:hi_x])
        if not hit.any():
            continue
        block = image[lo_y:hi_y, lo_x:hi_x]
        block[hit] = colours[i]
        zbuf[lo_y:hi_y, lo_x:hi_x][hit] = depth[i]

    Image.fromarray(image.astype(np.uint8)).save(path)
    return path


def bike_groups(js, binary):
    """The bike's own triangles, read back so the preview can show the fit."""
    def accessor(index):
        a = js['accessors'][index]
        view = js['bufferViews'][a['bufferView']]
        offset = view.get('byteOffset', 0) + a.get('byteOffset', 0)
        fmt = {5123: np.uint16, 5125: np.uint32, 5126: np.float32}[a['componentType']]
        count = a['count'] * (3 if a['type'] == 'VEC3' else 1)
        data = np.frombuffer(bytes(binary), dtype=fmt, count=count, offset=offset)
        return data.reshape(-1, 3) if a['type'] == 'VEC3' else data

    groups = {}
    for mesh in js['meshes']:
        for prim in mesh['primitives']:
            add_group(groups, -1, accessor(prim['attributes']['POSITION']),
                      accessor(prim['attributes']['NORMAL']),
                      accessor(prim['indices']).astype(np.uint32))
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', metavar='DIR', help='write side/front/top PNGs here')
    parser.add_argument('--cell', type=float, default=0.17,
                        help='distance-field sample spacing; smaller is smoother and heavier')
    args = parser.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    source = os.path.join(here, 'rider-model', 'scooter.glb')
    targets = [
        os.path.join(root, 'apps', 'mobile-android', 'rider', 'src', 'main', 'assets',
                     'scooter_rider.glb'),
        os.path.join(root, 'apps', 'web', 'public', 'delivery', 'rider.glb'),
    ]

    if not os.path.exists(source):
        raise SystemExit('no bike to dress: %s' % source)

    js, binary = load_glb(source)

    # The rider inherits the bike's own node transform rather than a copy of
    # the numbers: if the bike is ever re-exported at a different scale, the
    # rider follows it instead of hovering above it.
    donor = js['nodes'][0]
    transform = {k: donor[k] for k in ('rotation', 'scale', 'translation') if k in donor}

    groups = {}

    body = rider_parts()
    extents = [p.bounds() for p in body]
    low = np.min([b[0] for b in extents], axis=0)
    high = np.max([b[1] for b in extents], axis=0)
    verts, normals, faces, materials = surface(body, low, high, args.cell, k=7.0)
    split_by_material(verts, normals, faces, materials, groups)
    body_tris = len(faces)

    head = helmet_parts()
    low = np.array([-1.6, -5.9, -11.6], dtype=np.float32)
    high = np.array([1.6, -3.0, -8.9], dtype=np.float32)
    # A tighter blend than the body: a chin bar that melts into the shell is a
    # crash helmet with no chin bar.
    hverts, hnormals, hfaces, hmaterials = surface(head, low, high, args.cell * 0.85, k=14.0)
    hmaterials = paint_visor(hverts, hmaterials, hfaces)
    split_by_material(hverts, hnormals, hfaces, hmaterials, groups)

    slabs = Slabs()
    build_box(slabs)
    for material, (pos, nrm, idx) in slabs.groups.items():
        add_group(groups, material, np.array(pos, np.float32),
                  np.array(nrm, np.float32), np.array(idx, np.uint32))

    if args.preview:
        if not os.path.isdir(args.preview):
            os.makedirs(args.preview)
        shown = dict(groups)
        shown.update(bike_groups(js, binary))
        PREVIEW_RGB[-1] = (176, 178, 182)
        for view in ('side', 'front', 'top'):
            print('  preview %s' % render(shown, os.path.join(args.preview, 'rider-%s.png' % view), view))

    triangles = merge(js, binary, groups, transform)

    size = 0
    for target in targets:
        directory = os.path.dirname(target)
        if not os.path.isdir(directory):
            os.makedirs(directory)
        size = save_glb(target, js, binary)
        print('wrote %s' % target)

    top = min(float(p.min(axis=0)[2]) for p, _, _ in groups.values())
    print('  %d triangles (%d body, %d helmet+box), %d materials, %.0f KB'
          % (triangles, body_tris, triangles - body_tris, len(MATERIALS), size / 1024.0))
    print('  highest point z=%.2f (%.2f m above the tyres)' % (top, (GROUND_Z - top) * UNIT_M))


if __name__ == '__main__':
    sys.exit(main())
