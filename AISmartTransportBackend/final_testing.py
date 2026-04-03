"""
test_stored_routes.py
─────────────────────
Local test viewer for stored Firestore routes and stops.
Reads 'routes' and 'stops' collections and serves a polished
interactive map dashboard in the browser.

Usage:
    python test_stored_routes.py

Then open:  http://localhost:5050
"""

import json
import sys
import os
from flask import Flask, jsonify, render_template_string

# ── Firebase init ──────────────────────────────────────────────────────────────
# Reuse your project's existing Firebase initialisation so credentials
# are loaded from the same place as the rest of the backend.
try:
    from database.firebase import get_collection
    from firebase_admin import firestore
except ImportError:
    print("ERROR: Could not import database.firebase — run from project root.")
    sys.exit(1)

app = Flask(__name__)

# ── Data fetcher ───────────────────────────────────────────────────────────────

def fetch_all_routes():
    """Fetch every route + its stops from Firestore and return as plain dicts."""
    routes_col = get_collection('routes')
    stops_col  = get_collection('stops')

    routes_raw = routes_col.stream()
    result = []

    for r_doc in routes_raw:
        route = r_doc.to_dict()
        route['id'] = r_doc.id

        # Fetch each stop document
        stops = []
        for stop_id in (route.get('optimized_order') or route.get('stop_ids') or []):
            s_doc = stops_col.document(stop_id).get()
            if s_doc.exists:
                s = s_doc.to_dict()
                s['id'] = stop_id
                # Convert Firestore Timestamps to ISO strings so JSON can serialise them
                for key in ('created_at', 'updated_at'):
                    ts = s.get(key)
                    if hasattr(ts, 'isoformat'):
                        s[key] = ts.isoformat()
                    elif ts is not None:
                        s[key] = str(ts)
                stops.append(s)

        route['stops'] = stops

        # Serialise route timestamps
        for key in ('created_at', 'updated_at'):
            ts = route.get(key)
            if hasattr(ts, 'isoformat'):
                route[key] = ts.isoformat()
            elif ts is not None:
                route[key] = str(ts)

        result.append(route)

    # Sort by route_name R1, R2, ...
    result.sort(key=lambda r: r.get('route_name', ''))
    return result


# ── API endpoints ──────────────────────────────────────────────────────────────

@app.route('/api/routes')
def api_routes():
    try:
        return jsonify(fetch_all_routes())
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ── HTML dashboard ─────────────────────────────────────────────────────────────

HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>Route Inspector — AI Smart Transport</title>

<link rel="preconnect" href="https://fonts.googleapis.com"/>
<link href="https://fonts.googleapis.com/css2?family=Syne:wght@400;600;700;800&family=DM+Mono:wght@400;500&family=DM+Sans:wght@300;400;500&display=swap" rel="stylesheet"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

<style>
:root {
  --bg:        #0b0f1a;
  --surface:   #111827;
  --card:      #16202e;
  --border:    #1e2d42;
  --accent:    #00d4ff;
  --accent2:   #ff6b35;
  --green:     #22c55e;
  --yellow:    #fbbf24;
  --text:      #e2e8f0;
  --muted:     #64748b;
  --radius:    12px;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: 'DM Sans', sans-serif;
  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
  overflow-x: hidden;
}

/* ── Header ── */
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 32px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 999;
}
.logo {
  font-family: 'Syne', sans-serif;
  font-weight: 800;
  font-size: 1.2rem;
  letter-spacing: -0.02em;
  color: var(--accent);
}
.logo span { color: var(--text); }
.header-meta {
  font-family: 'DM Mono', monospace;
  font-size: 0.75rem;
  color: var(--muted);
}
#status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.72rem;
  font-family: 'DM Mono', monospace;
  background: rgba(0,212,255,0.08);
  border: 1px solid rgba(0,212,255,0.2);
  color: var(--accent);
}
#status-badge .dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.6s infinite;
}
@keyframes pulse {
  0%,100% { opacity:1; transform:scale(1); }
  50%      { opacity:0.4; transform:scale(0.7); }
}

/* ── Layout ── */
.layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  height: calc(100vh - 62px);
}

/* ── Sidebar ── */
.sidebar {
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-top {
  padding: 20px 20px 14px;
  border-bottom: 1px solid var(--border);
}
.sidebar-top h2 {
  font-family: 'Syne', sans-serif;
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
  margin-bottom: 12px;
}

/* ── Stats strip ── */
.stats {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 14px;
}
.stat {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 10px 8px;
  text-align: center;
}
.stat-val {
  font-family: 'Syne', sans-serif;
  font-size: 1.3rem;
  font-weight: 800;
  color: var(--accent);
  line-height: 1;
}
.stat-lbl {
  font-size: 0.65rem;
  color: var(--muted);
  margin-top: 3px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* ── Route list ── */
.route-list {
  overflow-y: auto;
  flex: 1;
  padding: 10px 12px;
}
.route-list::-webkit-scrollbar { width: 4px; }
.route-list::-webkit-scrollbar-track { background: transparent; }
.route-list::-webkit-scrollbar-thumb { background: var(--border); border-radius: 4px; }

.route-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px 14px 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color .2s, transform .15s, box-shadow .2s;
  position: relative;
  overflow: hidden;
}
.route-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--route-color, var(--accent));
  border-radius: 3px 0 0 3px;
}
.route-card:hover {
  border-color: var(--route-color, var(--accent));
  transform: translateX(3px);
  box-shadow: 0 4px 20px rgba(0,0,0,0.3);
}
.route-card.active {
  border-color: var(--route-color, var(--accent));
  background: rgba(0,212,255,0.04);
}
.route-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.route-badge {
  font-family: 'Syne', sans-serif;
  font-weight: 800;
  font-size: 1rem;
  color: var(--route-color, var(--accent));
}
.route-id-tag {
  font-family: 'DM Mono', monospace;
  font-size: 0.62rem;
  color: var(--muted);
  background: rgba(255,255,255,0.04);
  padding: 2px 7px;
  border-radius: 4px;
}
.route-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}
.pill {
  font-size: 0.68rem;
  padding: 3px 8px;
  border-radius: 20px;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--border);
  color: var(--muted);
  font-family: 'DM Mono', monospace;
  white-space: nowrap;
}
.pill.hi { color: var(--text); background: rgba(0,212,255,0.08); border-color: rgba(0,212,255,0.2); }

/* ── Map area ── */
.map-area {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
#map {
  flex: 1;
  background: #0d1520;
}

/* ── Stop detail panel ── */
.detail-panel {
  background: var(--surface);
  border-top: 1px solid var(--border);
  padding: 0;
  max-height: 260px;
  overflow: hidden;
  transition: max-height .3s ease;
}
.detail-panel.open { overflow-y: auto; }
.detail-panel::-webkit-scrollbar { width: 4px; }
.detail-panel::-webkit-scrollbar-thumb { background: var(--border); border-radius: 4px; }

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  background: var(--surface);
  z-index: 2;
}
.detail-title {
  font-family: 'Syne', sans-serif;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--muted);
}
.detail-close {
  background: none;
  border: none;
  color: var(--muted);
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  transition: color .15s, background .15s;
}
.detail-close:hover { color: var(--text); background: var(--border); }

.stops-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.78rem;
}
.stops-table th {
  text-align: left;
  padding: 8px 16px;
  font-family: 'DM Mono', monospace;
  font-size: 0.65rem;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--muted);
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  position: sticky;
  top: 41px;
}
.stops-table td {
  padding: 9px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  vertical-align: middle;
}
.stops-table tr:hover td { background: rgba(255,255,255,0.02); }
.stop-seq {
  font-family: 'DM Mono', monospace;
  font-size: 0.7rem;
  color: var(--muted);
  width: 32px;
}
.stop-name-cell { font-weight: 500; max-width: 200px; }
.stop-name-cell small { display: block; font-size: 0.65rem; color: var(--muted); font-family: 'DM Mono', monospace; margin-top: 2px; }
.num-tag {
  font-family: 'DM Mono', monospace;
  font-size: 0.7rem;
  color: var(--accent);
}
.merged-badge {
  display: inline-block;
  font-size: 0.6rem;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(251,191,36,0.12);
  border: 1px solid rgba(251,191,36,0.3);
  color: var(--yellow);
  font-family: 'DM Mono', monospace;
  margin-left: 4px;
  vertical-align: middle;
}

/* ── Loading overlay ── */
#loader {
  position: fixed;
  inset: 0;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  gap: 20px;
  transition: opacity .4s;
}
#loader.hidden { opacity: 0; pointer-events: none; }
.spinner {
  width: 44px; height: 44px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loader-text {
  font-family: 'DM Mono', monospace;
  font-size: 0.8rem;
  color: var(--muted);
  letter-spacing: 0.05em;
}

/* ── Empty state ── */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 10px;
  color: var(--muted);
  font-family: 'DM Mono', monospace;
  font-size: 0.8rem;
  text-align: center;
  padding: 20px;
}
.empty-icon { font-size: 2.5rem; opacity: 0.3; }

/* ── Error toast ── */
#toast {
  position: fixed;
  bottom: 24px; right: 24px;
  background: #7f1d1d;
  border: 1px solid #991b1b;
  color: #fca5a5;
  padding: 12px 18px;
  border-radius: 8px;
  font-family: 'DM Mono', monospace;
  font-size: 0.75rem;
  max-width: 360px;
  z-index: 9998;
  transform: translateY(80px);
  opacity: 0;
  transition: transform .3s, opacity .3s;
}
#toast.show { transform: translateY(0); opacity: 1; }

/* ── Leaflet custom markers ── */
.stop-marker-label {
  background: var(--surface);
  border: 2px solid currentColor;
  border-radius: 50%;
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  font-family: 'Syne', sans-serif;
  font-weight: 700;
  font-size: 0.65rem;
  color: currentColor;
  box-shadow: 0 2px 8px rgba(0,0,0,0.5);
}
</style>
</head>

<body>

<!-- Loading overlay -->
<div id="loader">
  <div class="spinner"></div>
  <div class="loader-text">Fetching routes from Firestore…</div>
</div>

<!-- Error toast -->
<div id="toast"></div>

<header>
  <div class="logo">ROUTE<span>INSPECTOR</span></div>
  <div id="status-badge"><span class="dot"></span><span id="status-text">connecting…</span></div>
  <div class="header-meta" id="fetch-time"></div>
</header>

<div class="layout">

  <!-- Sidebar -->
  <aside class="sidebar">
    <div class="sidebar-top">
      <h2>Fleet Overview</h2>
      <div class="stats">
        <div class="stat"><div class="stat-val" id="s-routes">—</div><div class="stat-lbl">Routes</div></div>
        <div class="stat"><div class="stat-val" id="s-stops">—</div><div class="stat-lbl">Stops</div></div>
        <div class="stat"><div class="stat-val" id="s-students">—</div><div class="stat-lbl">Students</div></div>
      </div>
    </div>
    <div class="route-list" id="route-list">
      <div class="empty"><div class="empty-icon">🗺️</div>No routes loaded yet</div>
    </div>
  </aside>

  <!-- Map + detail panel -->
  <div class="map-area">
    <div id="map"></div>
    <div class="detail-panel" id="detail-panel">
      <div class="detail-header">
        <div class="detail-title" id="detail-title">Select a route</div>
        <button class="detail-close" id="detail-close" title="Close">✕</button>
      </div>
      <table class="stops-table" id="stops-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Stop Name</th>
            <th>Students</th>
            <th>Fee / Student</th>
            <th>Dist to Uni</th>
            <th>Merged</th>
            <th>Coordinates</th>
          </tr>
        </thead>
        <tbody id="stops-body"></tbody>
      </table>
    </div>
  </div>

</div>

<script>
// ── Palette — one colour per route, cycling ────────────────────────────────
const PALETTE = [
  '#00d4ff','#ff6b35','#22c55e','#a855f7','#fbbf24',
  '#f43f5e','#06b6d4','#84cc16','#f97316','#8b5cf6',
  '#ec4899','#14b8a6','#eab308','#3b82f6','#ef4444',
];

// ── Map setup ──────────────────────────────────────────────────────────────
const map = L.map('map', { zoomControl: true, attributionControl: false });
L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
  maxZoom: 19, subdomains: 'abcd'
}).addTo(map);
L.control.attribution({ prefix: '© OpenStreetMap © CartoDB' }).addTo(map);

// University marker (centre)
let uniMarker = null;

// Track layers per route
const routeLayers = {};   // route_id → { polyline, stopMarkers[] }
let activeRouteId = null;

// ── Utility ────────────────────────────────────────────────────────────────
function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 5000);
}

function setStatus(txt, ok = true) {
  const el = document.getElementById('status-text');
  el.textContent = txt;
  document.getElementById('status-badge').style.borderColor =
    ok ? 'rgba(0,212,255,0.2)' : 'rgba(244,63,94,0.3)';
}

// ── Build stop marker ──────────────────────────────────────────────────────
function makeStopIcon(seq, color) {
  return L.divIcon({
    className: '',
    html: `<div class="stop-marker-label" style="color:${color};border-color:${color};">${seq}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    popupAnchor: [0, -16],
  });
}

// ── Render a route onto the map ────────────────────────────────────────────
function renderRoute(route, color, visible) {
  const id = route.id;

  // Polyline from geometry
  let polyline = null;
  if (route.geometry && route.geometry.length > 1) {
    const latlngs = route.geometry.map(p => [p.lat, p.lng]);
    polyline = L.polyline(latlngs, {
      color, weight: 4, opacity: 0.85,
      dashArray: null,
      lineCap: 'round', lineJoin: 'round',
    });
    if (visible) polyline.addTo(map);
  }

  // Stop markers
  const stopMarkers = (route.stops || []).map((stop, idx) => {
    const lat = stop.latitude;
    const lng = stop.longitude;
    if (lat == null || lng == null) return null;

    const seq = idx + 1;
    const icon = makeStopIcon(seq, color);

    const students = (stop.student_ids || []).join(', ') || '—';
    const fee = stop.fee_per_student_pkr != null
      ? `PKR ${stop.fee_per_student_pkr.toLocaleString()}` : '—';
    const dist = stop.distance_to_university_km != null
      ? `${stop.distance_to_university_km} km` : '—';
    const snap = stop.snap_distance_m != null
      ? `${Math.round(stop.snap_distance_m)} m` : '—';
    const merged = stop.is_merged ? '<span style="color:#fbbf24">⬡ merged</span>' : '—';

    const popup = `
      <div style="font-family:'DM Sans',sans-serif;font-size:13px;min-width:220px;color:#e2e8f0">
        <div style="font-family:'Syne',sans-serif;font-weight:700;font-size:15px;
                    color:${color};margin-bottom:8px;border-bottom:1px solid #1e2d42;padding-bottom:6px">
          Stop ${seq} — ${route.route_name || route.id}
        </div>
        <b style="font-size:13px">${stop.stop_name || '—'}</b>
        <div style="margin-top:8px;display:grid;grid-template-columns:1fr 1fr;gap:5px 12px;font-size:12px">
          <div style="color:#64748b">Students</div><div>${stop.student_count ?? '—'}</div>
          <div style="color:#64748b">Fee/student</div><div>${fee}</div>
          <div style="color:#64748b">Dist to uni</div><div>${dist}</div>
          <div style="color:#64748b">Snap dist</div><div>${snap}</div>
          <div style="color:#64748b">Merged</div><div>${merged}</div>
          <div style="color:#64748b">Stop ID</div>
          <div style="font-family:'DM Mono',monospace;font-size:10px;color:#64748b;word-break:break-all">${stop.id}</div>
        </div>
      </div>`;

    const marker = L.marker([lat, lng], { icon }).bindPopup(popup, { maxWidth: 280 });
    if (visible) marker.addTo(map);
    return marker;
  }).filter(Boolean);

  routeLayers[id] = { polyline, stopMarkers };
}

// ── Show / hide route layers ───────────────────────────────────────────────
function showOnly(routeId) {
  Object.entries(routeLayers).forEach(([rid, layers]) => {
    const on = rid === routeId;
    if (layers.polyline) {
      if (on) layers.polyline.addTo(map); else map.removeLayer(layers.polyline);
    }
    layers.stopMarkers.forEach(m => {
      if (on) m.addTo(map); else map.removeLayer(m);
    });
  });
  activeRouteId = routeId;
}

// ── Populate stops table ───────────────────────────────────────────────────
function populateStopsTable(route, color) {
  const body = document.getElementById('stops-body');
  body.innerHTML = '';

  (route.stops || []).forEach((stop, idx) => {
    const tr = document.createElement('tr');
    const fee = stop.fee_per_student_pkr != null
      ? `<span class="num-tag">PKR ${stop.fee_per_student_pkr.toLocaleString()}</span>` : '<span style="color:var(--muted)">—</span>';
    const dist = stop.distance_to_university_km != null
      ? `<span class="num-tag">${stop.distance_to_university_km} km</span>` : '—';
    const merged = stop.is_merged
      ? '<span class="merged-badge">merged</span>' : '<span style="color:var(--muted);font-size:0.7rem">—</span>';
    const lat = stop.latitude?.toFixed(5) ?? '—';
    const lng = stop.longitude?.toFixed(5) ?? '—';

    tr.innerHTML = `
      <td class="stop-seq" style="color:${color}">${idx + 1}</td>
      <td class="stop-name-cell">
        ${stop.stop_name || '—'}
        <small>${stop.id}</small>
      </td>
      <td><span class="num-tag">${stop.student_count ?? '—'}</span></td>
      <td>${fee}</td>
      <td>${dist}</td>
      <td>${merged}</td>
      <td style="font-family:'DM Mono',monospace;font-size:0.68rem;color:var(--muted)">${lat}, ${lng}</td>`;

    // Click row → open that stop's marker popup
    tr.style.cursor = 'pointer';
    tr.addEventListener('click', () => {
      const layers = routeLayers[route.id];
      if (layers && layers.stopMarkers[idx]) {
        const m = layers.stopMarkers[idx];
        map.setView(m.getLatLng(), 17, { animate: true });
        m.openPopup();
      }
    });

    body.appendChild(tr);
  });
}

// ── Select a route ─────────────────────────────────────────────────────────
function selectRoute(route, color) {
  // Sidebar cards
  document.querySelectorAll('.route-card').forEach(c => c.classList.remove('active'));
  const card = document.querySelector(`[data-rid="${route.id}"]`);
  if (card) card.classList.add('active');

  // Map
  showOnly(route.id);

  // Fit bounds
  const layers = routeLayers[route.id];
  const bounds = [];
  if (layers.polyline) layers.polyline.getLatLngs().forEach(ll => bounds.push(ll));
  layers.stopMarkers.forEach(m => bounds.push(m.getLatLng()));
  if (bounds.length) map.fitBounds(L.latLngBounds(bounds), { padding: [40, 40] });

  // Detail panel
  document.getElementById('detail-title').textContent =
    `${route.route_name || route.id}  ·  ${(route.stops || []).length} stops  ·  ${route.total_students ?? '—'} students  ·  ${route.total_distance_km ?? '—'} km`;
  populateStopsTable(route, color);
  const panel = document.getElementById('detail-panel');
  panel.classList.add('open');
}

// ── Close detail panel ────────────────────────────────────────────────────
document.getElementById('detail-close').addEventListener('click', () => {
  document.getElementById('detail-panel').classList.remove('open');
});

// ── Build sidebar card ─────────────────────────────────────────────────────
function buildRouteCard(route, color, idx) {
  const card = document.createElement('div');
  card.className = 'route-card';
  card.dataset.rid = route.id;
  card.style.setProperty('--route-color', color);

  const dist = route.total_distance_km != null ? `${route.total_distance_km} km` : '—';
  const dur  = route.estimated_time_hours != null
    ? `${(route.estimated_time_hours * 60).toFixed(0)} min` : '—';

  card.innerHTML = `
    <div class="route-card-header">
      <div class="route-badge">${route.route_name || route.id}</div>
      <div class="route-id-tag">${route.id}</div>
    </div>
    <div class="route-pills">
      <div class="pill hi">🚏 ${route.num_stops ?? (route.stops || []).length} stops</div>
      <div class="pill hi">👤 ${route.total_students ?? '—'} students</div>
      <div class="pill">📏 ${dist}</div>
      <div class="pill">⏱ ${dur}</div>
    </div>`;

  card.addEventListener('click', () => selectRoute(route, color));
  return card;
}

// ── Main: fetch + render ───────────────────────────────────────────────────
async function loadRoutes() {
  const t0 = Date.now();
  try {
    const res = await fetch('/api/routes');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const routes = await res.json();

    if (routes.error) throw new Error(routes.error);

    const elapsed = ((Date.now() - t0) / 1000).toFixed(2);
    document.getElementById('fetch-time').textContent = `loaded in ${elapsed}s`;

    // Stats
    const totalStops    = routes.reduce((a, r) => a + (r.stops || []).length, 0);
    const totalStudents = routes.reduce((a, r) => a + (r.total_students || 0), 0);
    document.getElementById('s-routes').textContent   = routes.length;
    document.getElementById('s-stops').textContent    = totalStops;
    document.getElementById('s-students').textContent = totalStudents;

    // Sidebar
    const list = document.getElementById('route-list');
    list.innerHTML = '';

    if (routes.length === 0) {
      list.innerHTML = '<div class="empty"><div class="empty-icon">📭</div>No routes in Firestore</div>';
      setStatus('no data', false);
    } else {
      routes.forEach((route, i) => {
        const color = PALETTE[i % PALETTE.length];
        renderRoute(route, color, false);
        list.appendChild(buildRouteCard(route, color, i));
      });

      // Add university marker if any stop has coordinates
      const firstStop = routes.flatMap(r => r.stops).find(s => s.latitude && s.longitude);
      if (firstStop) {
        // Centre map
        map.setView([firstStop.latitude, firstStop.longitude], 12);
      }

      // Auto-select first route
      selectRoute(routes[0], PALETTE[0]);
      setStatus(`${routes.length} routes live`);
    }

  } catch (err) {
    setStatus('error', false);
    showToast(`Failed to load routes: ${err.message}`);
    console.error(err);
  } finally {
    const loader = document.getElementById('loader');
    loader.classList.add('hidden');
    setTimeout(() => loader.remove(), 500);
  }
}

loadRoutes();
</script>
</body>
</html>"""

@app.route('/')
def index():
    return render_template_string(HTML)


if __name__ == '__main__':
    print("\n" + "="*55)
    print("  Route Inspector — AI Smart Transport System")
    print("="*55)
    print("  Open in browser:  http://localhost:5050")
    print("  Press Ctrl+C to stop.")
    print("="*55 + "\n")
    app.run(host='0.0.0.0', port=5050, debug=False)