package com.ridenow.app.maps

/** Small MapLibre style that fetches map imagery directly from OpenStreetMap's tile service. */
object OpenStreetMapStyle {
    const val json = """
        {
          "version": 8,
          "name": "OpenStreetMap",
          "sources": {
            "openstreetmap": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19,
              "attribution": "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"
            }
          },
          "layers": [
            {
              "id": "openstreetmap-raster",
              "type": "raster",
              "source": "openstreetmap",
              "minzoom": 0,
              "maxzoom": 22
            }
          ]
        }
    """.trimIndent()
}
