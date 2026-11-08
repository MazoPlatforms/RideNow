# Sources and licenses

Mazo Maps: https://github.com/MazoPlatforms/newmaps
Published site: https://mazoplatforms.github.io/newmaps/

Upstream files retrieved on 2026-11-02:

- `vendor/mazo/routing-graph.mjs`: `assets/js/routing-graph.mjs`; imported unchanged by the Node.js adapter.
- `vendor/mazo/place-delivery.mjs`: `assets/js/place-delivery.mjs`; the decoder is used unchanged.
- `vendor/mazo/LICENSE`: upstream GPL-3.0 license.
- `data/kattankudy-routing.json`: SHA-256 `8279754e58bd919d3f142d9a85a9e8c0a86f64ceee7cadc7f44b23a573f04586`.
- `data/search-index.json`: SHA-256 `bdedf275e0a3457fe2ee66db8fe692357b153cdc4a01e7b3eba9f078fd2edda9`.

The routing manifest records coverage and known limitations. Source OpenStreetMap data is © OpenStreetMap contributors and is subject to ODbL and attribution requirements. Upstream attribution and eligibility requirements for Mazo public-place data also continue to apply. Follow the applicable license terms for bundled code and data before distribution.

The backend does not call an external public OSRM routing server. Booking quotes are rejected when a route is unavailable.
