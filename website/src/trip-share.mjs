const message = document.querySelector('#message');
const details = document.querySelector('#details');
const token = location.hash.slice(1);
const labels = {
  SEARCHING: 'Finding a driver', DRIVER_ASSIGNED: 'Driver assigned', DRIVER_ARRIVING: 'Driver on the way',
  DRIVER_ARRIVED: 'Driver has arrived', IN_PROGRESS: 'Ride in progress', AT_STOP: 'At the stop',
  WAITING: 'Waiting at the stop', RETURNING: 'Returning',
};
let stopped = false;

function showText(id, value) {
  const node = document.getElementById(id);
  node.textContent = value || 'Not available';
  const row = node.closest('div');
  if (row) row.hidden = !value;
}

async function refresh() {
  if (!/^[A-Za-z0-9_-]{43}$/.test(token || '')) {
    message.textContent = 'This sharing link is invalid or has expired.';
    stopped = true;
    return;
  }
  try {
    const response = await fetch('/v1/public/trips', {
      method: 'POST', cache: 'no-store', credentials: 'omit',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) throw new Error('unavailable');
    const trip = await response.json();
    document.querySelector('#state').textContent = labels[trip.state] || 'Ride status updated';
    showText('pickup', trip.pickup);
    showText('destination', trip.destination);
    showText('driver', trip.driverName);
    showText('vehicle', trip.vehicle);
    showText('location', trip.driverLocation ? `${trip.driverLocation[1].toFixed(5)}, ${trip.driverLocation[0].toFixed(5)}` : null);
    document.querySelector('#updated').textContent = `Updated ${new Date(trip.updatedAt).toLocaleTimeString()}`;
    message.textContent = trip.driverLocation ? 'Trip details refresh automatically.' : 'Waiting for an active driver location.';
    details.hidden = false;
  } catch {
    message.textContent = 'Trip details are unavailable. The ride may have ended, or this link may have expired or been revoked.';
    details.hidden = true;
    stopped = true;
  }
}

await refresh();
const timer = window.setInterval(() => { if (stopped) window.clearInterval(timer); else void refresh(); }, 10_000);
