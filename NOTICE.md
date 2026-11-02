# மூலங்கள் மற்றும் உரிமங்கள்

Mazo Maps: https://github.com/MazoPlatforms/newmaps
Published site: https://mazoplatforms.github.io/newmaps/

2026-11-02 அன்று பெறப்பட்ட upstream கோப்புகள்:

- `vendor/mazo/routing-graph.mjs`: `assets/js/routing-graph.mjs`; Node.js adapter-ல் மாற்றமின்றி import செய்யப்படுகிறது.
- `vendor/mazo/place-delivery.mjs`: `assets/js/place-delivery.mjs`; decoder மாற்றமின்றி பயன்படுத்தப்படுகிறது.
- `vendor/mazo/LICENSE`: upstream GPL-3.0 உரிமம்.
- `data/kattankudy-routing.json`: SHA-256 `8279754e58bd919d3f142d9a85a9e8c0a86f64ceee7cadc7f44b23a573f04586`.
- `data/search-index.json`: SHA-256 `bdedf275e0a3457fe2ee66db8fe692357b153cdc4a01e7b3eba9f078fd2edda9`.

Routing manifest coverage மற்றும் அறியப்பட்ட குறைகளைச் சேமிக்கிறது. OpenStreetMap மூலத் தரவிற்கு © OpenStreetMap contributors; அதன் ODbL மற்றும் attribution தேவைகள் பொருந்தும். Mazo public-place தரவின் upstream attribution/eligibility விதிகளும் தொடரும். Distribution-க்கு முன் bundled code மற்றும் தரவிற்கான பொருத்தமான உரிம நிபந்தனைகளைப் பின்பற்றவும்.

Backend வெளிப்புற public OSRM routing server-ஐ அழைப்பதில்லை. Route கிடைக்காத இடத்தில் booking quote நிராகரிக்கப்படும்.
