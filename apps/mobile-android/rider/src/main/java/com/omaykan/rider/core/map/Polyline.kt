package com.omaykan.rider.core.map

/**
 * Google's encoded polyline, precision 5 — unpacked into points.
 *
 * This is the format `geometries=polyline` returns (see [RouteRepository]) and
 * the one the static map's `path-` overlay takes as-is, which is why nothing in
 * this app needed to decode one until a map was rendered on the device. A
 * server-drawn picture consumes the string; a line layer needs the coordinates.
 *
 * Hand-written rather than pulled from a library: it is twenty lines against a
 * dependency in the release graph, and the encoding has not changed since 2005.
 *
 * The algorithm, since the loop below is famously unreadable: values are stored
 * as deltas from the previous point, each delta zig-zag encoded so a sign
 * becomes the low bit, then split into five-bit groups, each group offset by 63
 * to land in printable ASCII with the high bit set on every group but the last.
 */
fun decodePolyline(encoded: String): List<MapPoint> {
    val points = ArrayList<MapPoint>(encoded.length / 4)
    var index = 0
    var lat = 0
    var lng = 0

    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var byte: Int

        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20 && index < encoded.length)

        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        shift = 0
        result = 0

        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20 && index < encoded.length)

        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        points += MapPoint(lat / 1e5, lng / 1e5)
    }

    return points
}
