const BATTERY_FIELDS = ['maxCapacity', 'maxPercent', 'minPercent', 'maxChargePower', 'maxDischargePower', 'rte', 'price'];
const TARIFF_FIELDS = ['importT1', 'importT2', 'exportT1', 'exportT2'];

const GROUPS = [
	{ title: 'Grid flows', rows: [
		['intervalsParsed', 'Intervals parsed', 'int'],
		['homeImportKwh', 'Home import (kWh)', 'kwh'],
		['homeExportKwh', 'Home export (kWh)', 'kwh'],
		['netImportKwh', 'Net import with battery (kWh)', 'kwh'],
		['netExportKwh', 'Net export with battery (kWh)', 'kwh'],
	]},
	{ title: 'Battery', rows: [
		['usableCapacityKwh', 'Usable capacity (kWh)', 'kwh'],
		['finalChargeKwh', 'Final charge (kWh)', 'kwh'],
		['totalChargedKwh', 'Total charged (kWh)', 'kwh'],
		['totalDischargedKwh', 'Total discharged (kWh)', 'kwh'],
		['equivalentFullCycles', 'Equivalent full cycles', 'num2'],
	]},
	{ title: 'Money', rows: [
		['costNoBattery', 'Cost without battery (EUR)', 'eur'],
		['costWithBattery', 'Cost with battery (EUR)', 'eur'],
		['periodSavings', 'Period savings (EUR)', 'eur'],
		['annualSavings', 'Annual savings (EUR)', 'eur'],
		['batteryPrice', 'Battery price (EUR)', 'eur'],
	]},
	{ title: 'ROI', rows: [
		['periodYears', 'Period length (years)', 'num2'],
		['roiYears', 'ROI (years)', 'roi'],
	]},
];

const nf2 = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const nfInt = new Intl.NumberFormat('en-US');

function formatValue(kind, v) {
	if (kind === 'roi') return v === -1 ? 'Never (no savings)' : nf2.format(v) + ' years';
	if (kind === 'int') return nfInt.format(v);
	return nf2.format(v);
}

function readNumbers(form, names) {
	const out = {};
	for (const n of names) out[n] = Number(form.elements[n].value);
	return out;
}

function jsonPart(obj) {
	return new Blob([JSON.stringify(obj)], { type: 'application/json' });
}

function renderResults(data) {
	const body = document.getElementById('resultsBody');
	body.innerHTML = '';
	for (const group of GROUPS) {
		const tbody = document.createElement('tbody');
		tbody.className = 'group';
		const headRow = document.createElement('tr');
		const headCell = document.createElement('th');
		headCell.colSpan = 2;
		headCell.scope = 'colgroup';
		headCell.textContent = group.title;
		headRow.appendChild(headCell);
		tbody.appendChild(headRow);
		for (const [key, label, kind] of group.rows) {
			const tr = document.createElement('tr');
			const th = document.createElement('th');
			th.scope = 'row';
			th.textContent = label;
			const td = document.createElement('td');
			td.className = 'num';
			td.textContent = formatValue(kind, data[key]);
			tr.appendChild(th);
			tr.appendChild(td);
			tbody.appendChild(tr);
		}
		body.appendChild(tbody);
	}
	document.getElementById('results').hidden = false;
}

// RFC 7807 fields the server may put on a ProblemDetail body. Anything else is
// rendered as a generic key/value pair so new exception handlers surface in the UI
// without a frontend change.
const PROBLEM_STANDARD_KEYS = new Set(['type', 'title', 'status', 'detail', 'instance']);

// Renders a single ProblemDetail extra value into the given element. Arrays of objects
// (e.g. validation `violations`: [{field, message}, ...]) render as a nested list so
// each entry's keys show up instead of "[object Object]". Plain scalars and arrays of
// scalars stay on one line.
function renderExtraValue(parent, value) {
	if (Array.isArray(value)) {
		if (value.every(item => item === null || typeof item !== 'object')) {
			parent.textContent = value.join(', ');
			return;
		}
		const ul = document.createElement('ul');
		ul.style.margin = '0';
		ul.style.paddingLeft = '1.25rem';
		for (const item of value) {
			const li = document.createElement('li');
			li.textContent = formatObjectEntry(item);
			ul.appendChild(li);
		}
		parent.appendChild(ul);
		return;
	}
	if (value !== null && typeof value === 'object') {
		parent.textContent = formatObjectEntry(value);
		return;
	}
	parent.textContent = String(value);
}

// "key: value, key: value" for plain objects; falls back to JSON for nested shapes.
function formatObjectEntry(obj) {
	if (obj === null || typeof obj !== 'object') return String(obj);
	const parts = [];
	for (const [k, v] of Object.entries(obj)) {
		parts.push(`${k}: ${v !== null && typeof v === 'object' ? JSON.stringify(v) : v}`);
	}
	return parts.join(', ');
}

function showError(title, detail, extras) {
	const status = document.getElementById('status');
	status.innerHTML = '';
	const article = document.createElement('article');
	article.setAttribute('role', 'alert');

	const h = document.createElement('strong');
	h.textContent = title || 'Request failed';
	article.appendChild(h);

	if (detail) {
		const p = document.createElement('p');
		p.style.marginTop = '0.5rem';
		p.style.marginBottom = '0';
		p.textContent = detail;
		article.appendChild(p);
	}

	if (extras && Object.keys(extras).length > 0) {
		const dl = document.createElement('dl');
		dl.style.marginTop = '0.75rem';
		dl.style.marginBottom = '0';
		for (const [k, v] of Object.entries(extras)) {
			const dt = document.createElement('dt');
			dt.textContent = k;
			dt.style.fontWeight = 'bold';
			const dd = document.createElement('dd');
			dd.style.marginLeft = '0';
			dd.style.marginBottom = '0.25rem';
			renderExtraValue(dd, v);
			dl.appendChild(dt);
			dl.appendChild(dd);
		}
		article.appendChild(dl);
	}

	status.appendChild(article);
	status.hidden = false;
}

async function showErrorFromResponse(response) {
	const contentType = response.headers.get('content-type') || '';
	if (contentType.includes('application/problem+json')) {
		try {
			const problem = await response.json();
			const extras = {};
			for (const [k, v] of Object.entries(problem)) {
				if (!PROBLEM_STANDARD_KEYS.has(k)) extras[k] = v;
			}
			showError(problem.title || `HTTP ${response.status}`, problem.detail, extras);
			return;
		} catch (_) {
			// fall through to raw-text fallback
		}
	}
	const text = await response.text();
	showError(`HTTP ${response.status} ${response.statusText}`, text || null, null);
}

function clearStatus() {
	const status = document.getElementById('status');
	status.innerHTML = '';
	status.hidden = true;
}

document.getElementById('calcForm').addEventListener('submit', async (event) => {
	event.preventDefault();
	const form = event.currentTarget;
	if (!form.reportValidity()) return;

	const btn = document.getElementById('submitBtn');
	btn.setAttribute('aria-busy', 'true');
	btn.disabled = true;
	clearStatus();
	document.getElementById('results').hidden = true;

	try {
		const fd = new FormData();
		fd.append('csv', form.elements['csv'].files[0]);
		fd.append('battery', jsonPart(readNumbers(form, BATTERY_FIELDS)));
		fd.append('tariffs', jsonPart(readNumbers(form, TARIFF_FIELDS)));

		const response = await fetch('/calculate', { method: 'POST', body: fd });
		if (!response.ok) {
			await showErrorFromResponse(response);
			return;
		}
		const data = await response.json();
		renderResults(data);
	} catch (err) {
		showError('Request failed', err.message || String(err), null);
	} finally {
		btn.removeAttribute('aria-busy');
		btn.disabled = false;
	}
});
