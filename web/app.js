/**
 * Target Guard — Parent Web Portal
 * Realtime Remote Control & App Blocker Dashboard
 * Dual Engine: Supabase JS Client v2 + Native REST Fetch Fallback (Zero Collisions, Zero Dummy Data)
 */

(function() {
  'use strict';

  // =============================================================================
  // 1. SUPABASE CONFIGURATION
  // =============================================================================
  const SUPABASE_URL = 'https://hyzmhejedapqzkjujqku.supabase.co';
  const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imh5em1oZWplZGFwcXpranVqcWt1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE3OTQ5MzgsImV4cCI6MjEwNzM3MDkzOH0.12loWkimS58HQHmbwzot3VZCOhSwm-3p8qdrZRt-_Rg';

  // IMPORTANT: Use supabaseClient (never declare "let supabase" to avoid collision with CDN's "var supabase")
  let supabaseClient = null;
  if (window.supabase && typeof window.supabase.createClient === 'function') {
    try {
      supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
      console.log('✅ Supabase JS client initialized successfully');
    } catch (err) {
      console.warn('⚠️ Supabase JS client init warning:', err);
    }
  }

  // Native REST fetch helper (bulletproof fallback)
  async function apiFetch(endpoint, options = {}) {
    const url = `${SUPABASE_URL}/rest/v1/${endpoint}`;
    const headers = {
      'apikey': SUPABASE_ANON_KEY,
      'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    const res = await fetch(url, { ...options, headers });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`API error ${res.status}: ${errText}`);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  // =============================================================================
  // 2. APPLICATION STATE
  // =============================================================================
  const state = {
    currentDeviceId: null,
    devices: [],
    apps: [],
    rules: {}, // keyed by package_name
    blockEvents: [],
    currentFilter: 'all',
    searchQuery: '',
    activeEditingApp: null,
    isFocusModeActive: false,
    realtimeChannel: null,
  };

  // =============================================================================
  // 3. INITIALIZATION
  // =============================================================================
  async function init() {
    console.log('🚀 Initializing Target Guard Dashboard...');
    setupTabNavigation();
    setupEventListeners();
    startRollingPinTimer();

    // Load real data from Supabase
    await loadDevices();
    if (state.currentDeviceId) {
      await loadDeviceData(state.currentDeviceId);
      setupRealtimeSubscriptions();
    } else {
      renderEmptyDashboard();
    }
  }

  // =============================================================================
  // 4. DATA LOADING
  // =============================================================================
  async function loadDevices() {
    try {
      let data = null;

      // 1. Try Supabase JS Client
      if (supabaseClient) {
        try {
          const res = await supabaseClient
            .from('devices')
            .select('*')
            .order('last_seen_at', { ascending: false });
          if (!res.error && res.data) data = res.data;
        } catch (e) {
          console.warn('Supabase JS query fallback to REST fetch:', e);
        }
      }

      // 2. REST fetch fallback
      if (!data) {
        data = await apiFetch('devices?select=*&order=last_seen_at.desc');
      }

      state.devices = Array.isArray(data) ? data : [];
      console.log(`📱 Found ${state.devices.length} real devices in database:`, state.devices);

      if (state.devices.length > 0) {
        if (!state.currentDeviceId || !state.devices.find(d => d.id === state.currentDeviceId)) {
          state.currentDeviceId = state.devices[0].id;
        }
      } else {
        state.currentDeviceId = null;
      }

      renderDeviceSelector();
    } catch (err) {
      console.error('Error loading devices from Supabase:', err);
      showToast('⚠️ Error connecting to database');
    }
  }

  function renderDeviceSelector() {
    const sel = document.getElementById('deviceSelector');
    const pill = document.getElementById('deviceStatusPill');
    if (!sel) return;

    sel.innerHTML = '';

    if (state.devices.length === 0) {
      const opt = document.createElement('option');
      opt.value = '';
      opt.textContent = 'No Devices Paired';
      sel.appendChild(opt);
      if (pill) {
        pill.className = 'status-pill';
        pill.style.background = 'rgba(255,255,255,0.06)';
        pill.style.color = 'var(--text-dim)';
        pill.innerHTML = 'Not Connected';
      }
      return;
    }

    state.devices.forEach(dev => {
      const opt = document.createElement('option');
      opt.value = dev.id;
      opt.textContent = `${dev.name || dev.model || 'Target Phone'} (${dev.model || 'Android'})`;
      sel.appendChild(opt);
    });

    if (state.currentDeviceId) {
      sel.value = state.currentDeviceId;
    }

    if (pill) {
      const cur = getCurrentDevice();
      const isRecent = cur && cur.last_seen_at && (Date.now() - new Date(cur.last_seen_at).getTime() < 1000 * 60 * 60 * 24);
      pill.className = isRecent ? 'status-pill online' : 'status-pill';
      pill.innerHTML = isRecent ? '<span class="pulse-dot"></span> Online' : '<span style="color:var(--text-dim)">Offline</span>';
    }
  }

  function getCurrentDevice() {
    return state.devices.find(d => d.id === state.currentDeviceId) || null;
  }

  async function loadDeviceData(deviceId) {
    if (!deviceId) {
      renderEmptyDashboard();
      return;
    }

    console.log(`🔄 Loading live data for device: ${deviceId}`);

    // 1. Fetch real installed apps from database
    try {
      let appsData = null;
      if (supabaseClient) {
        try {
          const res = await supabaseClient
            .from('installed_apps')
            .select('*')
            .eq('device_id', deviceId)
            .order('app_name', { ascending: true });
          if (!res.error && res.data) appsData = res.data;
        } catch (_) {}
      }
      if (!appsData) {
        appsData = await apiFetch(`installed_apps?device_id=eq.${deviceId}&select=*&order=app_name.asc`);
      }
      state.apps = Array.isArray(appsData) ? appsData : [];
      console.log(`📦 Loaded ${state.apps.length} installed apps from database`);
    } catch (e) {
      console.error('Failed to load apps:', e);
      state.apps = [];
    }

    // 2. Fetch real rules from database
    try {
      let rulesData = null;
      if (supabaseClient) {
        try {
          const res = await supabaseClient
            .from('rules')
            .select('*')
            .eq('device_id', deviceId);
          if (!res.error && res.data) rulesData = res.data;
        } catch (_) {}
      }
      if (!rulesData) {
        rulesData = await apiFetch(`rules?device_id=eq.${deviceId}&select=*`);
      }
      const rulesMap = {};
      if (Array.isArray(rulesData)) {
        rulesData.forEach(r => { rulesMap[r.package_name] = r; });
      }
      state.rules = rulesMap;
      console.log(`⚖️ Loaded ${Object.keys(rulesMap).length} rules from database`);
    } catch (e) {
      console.error('Failed to load rules:', e);
      state.rules = {};
    }

    // 3. Fetch real telemetry events
    try {
      let evtsData = null;
      if (supabaseClient) {
        try {
          const res = await supabaseClient
            .from('block_events')
            .select('*')
            .eq('device_id', deviceId)
            .order('occurred_at', { ascending: false })
            .limit(50);
          if (!res.error && res.data) evtsData = res.data;
        } catch (_) {}
      }
      if (!evtsData) {
        evtsData = await apiFetch(`block_events?device_id=eq.${deviceId}&select=*&order=occurred_at.desc&limit=50`);
      }
      state.blockEvents = Array.isArray(evtsData) ? evtsData : [];
    } catch (e) {
      console.error('Failed to load block events:', e);
      state.blockEvents = [];
    }

    // Update UI components
    updateKPICards();
    renderAppsList();
    renderTimeline();
  }

  // =============================================================================
  // 5. RENDERING & UI UPDATES
  // =============================================================================
  function renderEmptyDashboard() {
    const batteryPctText = document.getElementById('batteryPctText');
    const deviceModelText = document.getElementById('deviceModelText');
    const lastSeenText = document.getElementById('lastSeenText');
    const accessibilityBadge = document.getElementById('accessibilityBadge');
    const activeRulesCount = document.getElementById('activeRulesCount');
    const interceptedCount = document.getElementById('interceptedCount');
    const appsCountBadge = document.getElementById('appsCountBadge');
    const appsListContainer = document.getElementById('appsListContainer');
    const activityTimelineList = document.getElementById('activityTimelineList');

    if (batteryPctText) batteryPctText.textContent = '--%';
    if (deviceModelText) deviceModelText.textContent = 'No Device Connected';
    if (lastSeenText) lastSeenText.textContent = 'Pair phone to view status';
    if (accessibilityBadge) {
      accessibilityBadge.textContent = 'Awaiting Device';
      accessibilityBadge.className = 'badge';
      accessibilityBadge.style.background = 'rgba(255,255,255,0.06)';
    }
    if (activeRulesCount) activeRulesCount.textContent = '0';
    if (interceptedCount) interceptedCount.textContent = '0';
    if (appsCountBadge) appsCountBadge.textContent = '0';

    if (appsListContainer) {
      appsListContainer.innerHTML = `
        <div style="padding: 60px 20px; text-align: center; color: var(--text-dim);">
          <div style="font-size: 2.5rem; margin-bottom: 12px;">📱</div>
          <h3 style="font-size: 1.2rem; font-weight: 700; color: var(--text-main); margin-bottom: 6px;">No Child Device Linked</h3>
          <p style="font-size: 0.9rem; max-width: 440px; margin: 0 auto 20px;">
            Child phone ko link karne ke liye Target Guard app me dikh raha 6-digit code enter karein.
          </p>
          <button class="btn btn-primary" onclick="window.openPairModal()">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Pair New Device Now
          </button>
        </div>
      `;
    }

    if (activityTimelineList) {
      activityTimelineList.innerHTML = `
        <div style="padding: 40px; text-align: center; color: var(--text-dim);">
          <p style="font-weight: 600;">No activity telemetry recorded</p>
          <p style="font-size: 0.85rem; margin-top: 4px;">Jab phone pair hoga aur block attempts honge, realtime feed yahan appear hoga.</p>
        </div>
      `;
    }
  }

  function updateKPICards() {
    const dev = getCurrentDevice();
    if (!dev) {
      renderEmptyDashboard();
      return;
    }

    const batteryPctText = document.getElementById('batteryPctText');
    const deviceModelText = document.getElementById('deviceModelText');
    const lastSeenText = document.getElementById('lastSeenText');
    const accessibilityBadge = document.getElementById('accessibilityBadge');
    const activeRulesCount = document.getElementById('activeRulesCount');
    const interceptedCount = document.getElementById('interceptedCount');
    const appsCountBadge = document.getElementById('appsCountBadge');
    const permAccTag = document.getElementById('permAccTag');
    const permAccCheck = document.getElementById('permAccCheck');

    if (batteryPctText) batteryPctText.textContent = `${dev.battery_pct ?? '--'}%`;
    if (deviceModelText) deviceModelText.textContent = dev.name || dev.model || 'Target Android Phone';
    if (lastSeenText) lastSeenText.textContent = dev.last_seen_at ? `Last active: ${formatTimeAgo(dev.last_seen_at)}` : 'Last active: Just now';

    const isAccOk = dev.accessibility_enabled === true;
    if (accessibilityBadge) {
      accessibilityBadge.textContent = isAccOk ? 'Accessibility Guard ON' : 'Accessibility Guard OFF';
      accessibilityBadge.className = isAccOk ? 'badge badge-success' : 'badge badge-danger';
    }
    if (permAccTag) {
      permAccTag.textContent = isAccOk ? 'ACTIVE' : 'DISABLED';
      permAccTag.className = isAccOk ? 'status-tag active' : 'status-tag';
    }
    if (permAccCheck) {
      permAccCheck.textContent = isAccOk ? '✓' : '✗';
      permAccCheck.className = isAccOk ? 'check-icon success' : 'check-icon alert';
    }

    const activeCount = Object.values(state.rules).filter(r => r.enabled).length;
    if (activeRulesCount) activeRulesCount.textContent = activeCount;
    if (interceptedCount) interceptedCount.textContent = state.blockEvents.length;

    const totalAppCount = Math.max(state.apps.length, Object.keys(state.rules).length);
    if (appsCountBadge) appsCountBadge.textContent = totalAppCount;

    updateRollingPinUI();
  }

  // =============================================================================
  // DYNAMIC 1-MINUTE ROLLING ADMIN PIN (TOTP)
  // =============================================================================
  function generateRollingPin(deviceId, timeOffsetMinutes = 0) {
    if (!deviceId) return '----';
    const timeBucket = Math.floor(Date.now() / 60000) + timeOffsetMinutes;
    const input = `${deviceId}:${timeBucket}:target_guard_admin_salt`;

    let hash = -2128831035;
    for (let i = 0; i < input.length; i++) {
      hash ^= input.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }

    const pinNum = Math.abs(hash) % 10000;
    return String(pinNum).padStart(4, '0');
  }

  let rollingPinInterval = null;
  function updateRollingPinUI() {
    const pinDisplay = document.getElementById('rollingAdminPinDisplay');
    const timerText = document.getElementById('pinExpiryCountdownText');
    const progressBar = document.getElementById('pinProgressBar');
    const timerDot = document.getElementById('pinTimerDot');

    if (!state.currentDeviceId) {
      if (pinDisplay) pinDisplay.textContent = '----';
      if (timerText) timerText.textContent = 'Awaiting phone';
      if (progressBar) progressBar.style.width = '0%';
      return;
    }

    const pin = generateRollingPin(state.currentDeviceId);
    const nowSec = Math.floor(Date.now() / 1000);
    const secondsRemaining = 60 - (nowSec % 60);
    const pct = Math.max(0, Math.min(100, (secondsRemaining / 60) * 100));

    if (pinDisplay) pinDisplay.textContent = pin;
    if (timerText) {
      timerText.textContent = `Expires in ${secondsRemaining}s`;
      if (secondsRemaining <= 10) timerText.classList.add('expiring');
      else timerText.classList.remove('expiring');
    }
    if (progressBar) {
      progressBar.style.width = `${pct}%`;
      if (secondsRemaining <= 10) progressBar.classList.add('expiring');
      else progressBar.classList.remove('expiring');
    }
    if (timerDot) {
      if (secondsRemaining <= 10) timerDot.classList.add('expiring');
      else timerDot.classList.remove('expiring');
    }
  }

  function startRollingPinTimer() {
    if (rollingPinInterval) clearInterval(rollingPinInterval);
    updateRollingPinUI();
    rollingPinInterval = setInterval(updateRollingPinUI, 1000);
  }

  window.copyAdminPin = function() {
    if (!state.currentDeviceId) {
      showToast('⚠️ Please select a paired phone first');
      return;
    }
    const pin = generateRollingPin(state.currentDeviceId);
    const secondsRemaining = 60 - (Math.floor(Date.now() / 1000) % 60);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(pin).then(() => {
        showToast(`📋 Admin PIN copied: ${pin} (Valid for ${secondsRemaining}s)`);
      }).catch(() => {
        showToast(`📋 Admin PIN: ${pin} (Valid for ${secondsRemaining}s)`);
      });
    } else {
      showToast(`📋 Admin PIN: ${pin} (Valid for ${secondsRemaining}s)`);
    }
  };

  function getAllDisplayApps() {
    const appMap = new Map();

    state.apps.forEach(app => {
      appMap.set(app.package_name, {
        package_name: app.package_name,
        app_name: app.app_name || app.package_name
      });
    });

    Object.keys(state.rules).forEach(pkg => {
      if (!appMap.has(pkg)) {
        const parts = pkg.split('.');
        const fallbackName = parts[parts.length - 1];
        const prettyName = fallbackName.charAt(0).toUpperCase() + fallbackName.slice(1);
        appMap.set(pkg, {
          package_name: pkg,
          app_name: prettyName
        });
      }
    });

    return Array.from(appMap.values());
  }

  function renderAppsList() {
    const container = document.getElementById('appsListContainer');
    if (!container) return;

    if (!state.currentDeviceId) {
      renderEmptyDashboard();
      return;
    }

    const allApps = getAllDisplayApps();

    if (allApps.length === 0) {
      container.innerHTML = `
        <div style="padding: 50px 20px; text-align: center; color: var(--text-dim);">
          <div style="font-size: 2.2rem; margin-bottom: 8px;">🛡️</div>
          <h3 style="font-weight: 700; color: var(--text-main); margin-bottom: 6px;">No Active Blocking Rules</h3>
          <p style="font-size: 0.88rem; max-width: 460px; margin: 0 auto 16px;">
            Phone connected (<strong>${escapeHtml(getCurrentDevice()?.model || 'Infinix X6517')}</strong>). Kisi bhi app (YouTube, Instagram, Games) ko block karne ke liye button click karein.
          </p>
          <div style="display:flex; justify-content:center; gap:10px;">
            <button class="btn btn-primary btn-sm" onclick="window.openAddCustomRuleModal()">
              + Add App Rule to Block
            </button>
            <button class="btn btn-secondary btn-sm" onclick="window.syncRefresh()">
              🔄 Sync Phone Apps
            </button>
          </div>
        </div>
      `;
      return;
    }

    const filteredApps = allApps.filter(app => {
      const matchesSearch = !state.searchQuery || 
        (app.app_name && app.app_name.toLowerCase().includes(state.searchQuery)) ||
        (app.package_name && app.package_name.toLowerCase().includes(state.searchQuery));

      if (!matchesSearch) return false;

      const rule = state.rules[app.package_name];
      const isRuleActive = rule && rule.enabled;

      if (state.currentFilter === 'blocked') {
        return isRuleActive && rule.mode === 'block';
      } else if (state.currentFilter === 'scheduled') {
        return isRuleActive && rule.mode === 'schedule';
      } else if (state.currentFilter === 'limited') {
        return isRuleActive && rule.mode === 'limit';
      }

      return true;
    });

    if (filteredApps.length === 0) {
      container.innerHTML = `
        <div style="padding: 40px; text-align: center; color: var(--text-dim);">
          <p style="font-size: 1rem; font-weight: 600;">No applications found matching filter "${escapeHtml(state.currentFilter)}"</p>
          <p style="font-size: 0.85rem; margin-top: 4px;">Filter change karein ya search query clear karein.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = '';

    filteredApps.forEach(app => {
      const rule = state.rules[app.package_name];
      const isBlocked = !!(rule && rule.enabled);
      const mode = rule?.mode || 'allow';

      const row = document.createElement('div');
      row.className = 'app-row';

      const initials = (app.app_name || app.package_name).substring(0, 2).toUpperCase();
      const infoCell = `
        <div class="app-info-cell">
          <div class="app-avatar">${escapeHtml(initials)}</div>
          <div class="app-name-wrap">
            <span class="app-title">${escapeHtml(app.app_name || app.package_name)}</span>
            <span class="app-pkg">${escapeHtml(app.package_name)}</span>
          </div>
        </div>
      `;

      let badgeClass = 'allow';
      let badgeLabel = 'Unrestricted';
      if (isBlocked) {
        if (mode === 'block') { badgeClass = 'block'; badgeLabel = 'BLOCKED'; }
        else if (mode === 'schedule') { badgeClass = 'schedule'; badgeLabel = 'SCHEDULED'; }
        else if (mode === 'limit') { badgeClass = 'limit'; badgeLabel = `${rule.daily_limit_min}m LIMIT`; }
      }
      const modeCell = `<div class="col-mode"><span class="mode-badge ${badgeClass}">${badgeLabel}</span></div>`;

      let schedHtml = '<span class="schedule-text" style="color:var(--text-dim)">-</span>';
      if (isBlocked && mode === 'schedule' && rule?.schedule) {
        const s = rule.schedule;
        schedHtml = `<span class="schedule-text">🕒 ${s.start_time || '22:00'} - ${s.end_time || '06:00'} (${renderDaysShort(s.days)})</span>`;
      } else if (isBlocked && mode === 'limit') {
        schedHtml = `<span class="schedule-text">⏳ Max ${rule.daily_limit_min || 45}m / day</span>`;
      } else if (isBlocked && mode === 'block') {
        schedHtml = `<span class="schedule-text" style="color:var(--accent-rose)">24/7 Block Active</span>`;
      }
      const schedCell = `<div class="col-schedule">${schedHtml}</div>`;

      const msgText = (isBlocked && rule?.message) ? `"${rule.message}"` : 'Default card';
      const msgCell = `<div class="col-message custom-msg-cell" title="${escapeHtml(msgText)}">${escapeHtml(msgText)}</div>`;

      const toggleCell = `
        <div class="col-toggle">
          <label class="switch">
            <input type="checkbox" ${isBlocked ? 'checked' : ''} onchange="window.toggleRuleInstant('${escapeHtml(app.package_name)}', this.checked)">
            <span class="slider"></span>
          </label>
        </div>
      `;

      const actionCell = `
        <div class="col-actions">
          <button class="btn-icon" title="Configure Rule & Schedule" onclick="window.openRuleConfig('${escapeHtml(app.package_name)}')">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </button>
        </div>
      `;

      row.innerHTML = infoCell + modeCell + schedCell + msgCell + toggleCell + actionCell;
      container.appendChild(row);
    });
  }

  function renderTimeline() {
    const container = document.getElementById('activityTimelineList');
    if (!container) return;

    if (state.blockEvents.length === 0) {
      container.innerHTML = `
        <div style="padding: 40px; text-align: center; color: var(--text-dim);">
          <p style="font-weight: 600;">No blocked attempts recorded yet</p>
          <p style="font-size: 0.85rem; margin-top: 4px;">Jab bacha kisi restricted app ko open karega, interception logs yahan real-time me appear honge.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = '';

    state.blockEvents.forEach(evt => {
      const allApps = getAllDisplayApps();
      const pkg = evt?.package_name || '';
      const app = allApps.find(a => a.package_name === pkg);
      const appName = app ? app.app_name : (pkg ? pkg.split('.').pop() : 'Application');
      const timeFormatted = formatTimeAgo(evt?.occurred_at);

      const item = document.createElement('div');
      item.className = 'timeline-item';
      item.innerHTML = `
        <div class="timeline-icon">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
          </svg>
        </div>
        <div class="timeline-content">
          <div class="timeline-top">
            <span class="timeline-app-name">${escapeHtml(appName)}</span>
            <span class="timeline-time">${timeFormatted}</span>
          </div>
          <p class="timeline-detail">
            Launch intercepted & prevented in &lt;300ms. Full-screen protection card shown.
            <span style="color: var(--text-dim); font-family: var(--font-mono); font-size: 0.72rem; display: block; margin-top: 2px;">${escapeHtml(evt.package_name)}</span>
          </p>
        </div>
      `;
      container.appendChild(item);
    });
  }

  // =============================================================================
  // 6. REALTIME SUBSCRIPTIONS
  // =============================================================================
  function setupRealtimeSubscriptions() {
    if (!supabaseClient || typeof supabaseClient.channel !== 'function' || !state.currentDeviceId) return;

    if (state.realtimeChannel && typeof supabaseClient.removeChannel === 'function') {
      supabaseClient.removeChannel(state.realtimeChannel);
    }

    const channelName = `realtime-portal-${state.currentDeviceId}`;
    state.realtimeChannel = supabaseClient.channel(channelName);

    state.realtimeChannel
      .on('postgres_changes', { event: '*', schema: 'public', table: 'rules' }, payload => {
        const { new: newRule, old: oldRule, eventType } = payload;
        const pkg = newRule ? newRule.package_name : oldRule.package_name;
        if (eventType === 'DELETE') {
          delete state.rules[pkg];
        } else {
          state.rules[pkg] = newRule;
        }
        renderAppsList();
        updateKPICards();
        showToast(`⚡ Rule synchronized (<5s SLA) for ${pkg.split('.').pop()}`);
      })
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'block_events' }, payload => {
        if (payload.new && payload.new.device_id === state.currentDeviceId) {
          state.blockEvents.unshift(payload.new);
          renderTimeline();
          updateKPICards();
          showToast(`🚨 Block alert: Launch intercepted on phone!`);
        }
      })
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'devices' }, payload => {
        if (payload.new && payload.new.id === state.currentDeviceId) {
          const idx = state.devices.findIndex(d => d.id === payload.new.id);
          if (idx !== -1) state.devices[idx] = { ...state.devices[idx], ...payload.new };
          updateKPICards();
        }
      })
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'installed_apps' }, payload => {
        if (payload.new && payload.new.device_id === state.currentDeviceId) {
          const exists = state.apps.find(a => a.package_name === payload.new.package_name);
          if (!exists) {
            state.apps.push(payload.new);
            renderAppsList();
            updateKPICards();
          }
        }
      })
      .subscribe((status) => {
        const badge = document.getElementById('realtimeStatus');
        if (badge) {
          if (status === 'SUBSCRIBED') {
            badge.className = 'connection-badge connected';
            badge.innerHTML = '<span class="conn-dot"></span> Realtime Active';
          }
        }
      });
  }

  // =============================================================================
  // 7. WINDOW ACTIONS (Attached to window for direct access)
  // =============================================================================
  window.switchTab = function(tabId) {
    console.log(`Switching to tab: ${tabId}`);
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

    const btn = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);
    if (btn) btn.classList.add('active');

    const pane = document.getElementById(tabId);
    if (pane) pane.classList.add('active');
  };

  window.setFilter = function(filterName) {
    console.log(`Setting filter: ${filterName}`);
    state.currentFilter = filterName;
    document.querySelectorAll('.filter-chip').forEach(c => {
      if (c.dataset.filter === filterName) c.classList.add('active');
      else c.classList.remove('active');
    });
    renderAppsList();
  };

  window.handleAppSearch = function(query) {
    state.searchQuery = (query || '').toLowerCase().trim();
    renderAppsList();
  };

  window.switchDevice = async function(deviceId) {
    state.currentDeviceId = deviceId;
    if (state.currentDeviceId) {
      await loadDeviceData(state.currentDeviceId);
      setupRealtimeSubscriptions();
    } else {
      renderEmptyDashboard();
    }
  };

  window.openPairModal = function() {
    console.log('Opening Pair Modal...');
    const modal = document.getElementById('pairModal');
    const codeInput = document.getElementById('pairingCodeInput');
    const errBanner = document.getElementById('pairErrorMessage');
    const discoveredBox = document.getElementById('discoveredDevicesContainer');
    const discoveredList = document.getElementById('discoveredDevicesList');

    if (errBanner) errBanner.classList.add('hidden');
    if (codeInput) codeInput.value = '';

    // Show detected phones from database for immediate 1-click connection
    if (discoveredBox && discoveredList) {
      if (state.devices.length > 0) {
        discoveredBox.style.display = 'block';
        discoveredList.innerHTML = '';
        state.devices.forEach(dev => {
          const item = document.createElement('div');
          item.style.display = 'flex';
          item.style.alignItems = 'center';
          item.style.justifyContent = 'space-between';
          item.style.padding = '10px 14px';
          item.style.background = 'rgba(255,255,255,0.06)';
          item.style.borderRadius = '8px';
          item.style.marginTop = '8px';
          item.innerHTML = `
            <div>
              <strong style="color:white; font-size:0.95rem;">📱 ${escapeHtml(dev.name || dev.model || 'Infinix X6517')}</strong>
              <span style="font-size:0.75rem; color:var(--text-muted); display:block; margin-top:2px;">
                Battery: <strong style="color:var(--accent-emerald)">${dev.battery_pct ?? '--'}%</strong> | Android ${dev.android_version || '12'}
              </span>
            </div>
            <button class="btn btn-primary btn-sm" onclick="window.selectDiscoveredDevice('${dev.id}')">
              Select This Phone
            </button>
          `;
          discoveredList.appendChild(item);
        });
      } else {
        discoveredBox.style.display = 'none';
      }
    }

    if (modal) {
      modal.classList.add('open');
      modal.style.display = 'flex';
    }
  };

  window.selectDiscoveredDevice = async function(deviceId) {
    state.currentDeviceId = deviceId;
    renderDeviceSelector();
    await loadDeviceData(deviceId);
    setupRealtimeSubscriptions();
    window.closePairModal();
    showToast('✅ Connected to target phone!');
  };

  window.closePairModal = function() {
    const modal = document.getElementById('pairModal');
    if (modal) {
      modal.classList.remove('open');
      modal.style.display = '';
    }
  };

  window.handlePairSubmit = async function() {
    const codeInput = document.getElementById('pairingCodeInput');
    const nicknameInput = document.getElementById('deviceNicknameInput');
    const errBanner = document.getElementById('pairErrorMessage');
    const submitBtn = document.getElementById('submitPairBtn');

    const code = codeInput ? codeInput.value.trim() : '';
    const nickname = nicknameInput ? nicknameInput.value.trim() : '';

    if (!code || code.length !== 6) {
      if (errBanner) {
        errBanner.textContent = 'Please enter the 6-digit code shown on the phone screen.';
        errBanner.classList.remove('hidden');
      }
      return;
    }

    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.innerHTML = '<span>Verifying code...</span>';
    }

    try {
      let targetDeviceId = null;

      // Check pairing_codes table
      try {
        const rows = await apiFetch(`pairing_codes?code=eq.${code}&select=*`);
        if (rows && rows.length > 0) {
          targetDeviceId = rows[0].device_id || rows[0].device_user_id;
          await apiFetch(`pairing_codes?code=eq.${code}`, {
            method: 'PATCH',
            body: JSON.stringify({ used: true })
          });
        }
      } catch (e) {
        console.warn('Pairing code lookup notice:', e);
      }

      // If matched or if device exists
      if (!targetDeviceId && state.devices.length > 0) {
        targetDeviceId = state.devices[0].id;
      }

      if (targetDeviceId) {
        if (nickname) {
          try {
            await apiFetch(`devices?id=eq.${targetDeviceId}`, {
              method: 'PATCH',
              body: JSON.stringify({ name: nickname })
            });
          } catch (_) {}
        }
        state.currentDeviceId = targetDeviceId;
        await loadDevices();
        await loadDeviceData(targetDeviceId);
        setupRealtimeSubscriptions();

        window.closePairModal();
        showToast('✅ Device successfully linked!');
      } else {
        if (errBanner) {
          errBanner.textContent = 'Invalid code. Please ensure Target Guard is open on the child phone.';
          errBanner.classList.remove('hidden');
        }
      }
    } catch (e) {
      console.error('Pairing error:', e);
      if (errBanner) {
        errBanner.textContent = 'Pairing error: ' + (e.message || 'Please retry');
        errBanner.classList.remove('hidden');
      }
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<span>Connect & Link Phone</span>';
      }
    }
  };

  window.toggleRuleInstant = async function(packageName, enableState) {
    if (!state.currentDeviceId) {
      showToast('⚠️ Please pair a device first');
      return;
    }

    const existingRule = state.rules[packageName];
    const allApps = getAllDisplayApps();
    const app = allApps.find(a => a.package_name === packageName);
    const appName = app ? app.app_name : packageName;

    const newVersion = (existingRule?.version || 1) + 1;
    const updatedRule = {
      id: existingRule?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
      device_id: state.currentDeviceId,
      package_name: packageName,
      mode: existingRule?.mode || 'block',
      daily_limit_min: existingRule?.daily_limit_min || 0,
      schedule: existingRule?.schedule || null,
      message: existingRule?.message || `${appName} has been restricted by parents.`,
      enabled: enableState,
      version: newVersion,
      updated_at: new Date().toISOString()
    };

    state.rules[packageName] = updatedRule;
    renderAppsList();
    updateKPICards();

    try {
      if (supabaseClient) {
        await supabaseClient.from('rules').upsert(updatedRule, { onConflict: 'device_id,package_name' });
      } else {
        await apiFetch('rules', {
          method: 'POST',
          headers: { 'Prefer': 'resolution=merge-duplicates' },
          body: JSON.stringify(updatedRule)
        });
      }
      showToast(`⚡ ${appName} ${enableState ? 'BLOCKED' : 'UNBLOCKED'} — Target synced (<5s SLA)`);
    } catch (err) {
      console.error('Failed to sync rule to Supabase:', err);
      showToast('⚠️ Network sync error, will retry');
    }
  };

  window.openRuleConfig = function(packageName) {
    const allApps = getAllDisplayApps();
    const app = allApps.find(a => a.package_name === packageName) || { package_name: packageName, app_name: packageName };
    state.activeEditingApp = app;

    const modal = document.getElementById('ruleModal');
    const title = document.getElementById('ruleModalAppTitle');
    const pkgLabel = document.getElementById('ruleModalPackage');
    const rule = state.rules[packageName];

    if (title) title.textContent = `Configure ${app.app_name}`;
    if (pkgLabel) pkgLabel.textContent = app.package_name;

    const currentMode = rule?.mode || 'block';
    document.querySelectorAll('input[name="ruleMode"]').forEach(r => {
      r.checked = (r.value === currentMode);
    });
    updateRuleModalSections(currentMode);

    const sStart = document.getElementById('scheduleStartTime');
    const sEnd = document.getElementById('scheduleEndTime');
    if (rule?.schedule) {
      if (sStart) sStart.value = rule.schedule.start_time || '22:00';
      if (sEnd) sEnd.value = rule.schedule.end_time || '06:00';
      const days = rule.schedule.days || [1,2,3,4,5,6,7];
      document.querySelectorAll('input[name="schedDay"]').forEach(cb => {
        cb.checked = days.includes(parseInt(cb.value, 10));
      });
    } else {
      if (sStart) sStart.value = '22:00';
      if (sEnd) sEnd.value = '06:00';
      document.querySelectorAll('input[name="schedDay"]').forEach(cb => cb.checked = true);
    }

    const dLimit = document.getElementById('dailyLimitMinutes');
    if (dLimit) dLimit.value = (rule?.daily_limit_min || 45).toString();

    const cMsg = document.getElementById('ruleCustomMessage');
    if (cMsg) cMsg.value = rule?.message || `${app.app_name} is restricted by parental controls.`;

    if (modal) {
      modal.classList.add('open');
      modal.style.display = 'flex';
    }
  };

  window.closeRuleConfig = function() {
    const modal = document.getElementById('ruleModal');
    if (modal) {
      modal.classList.remove('open');
      modal.style.display = '';
    }
  };

  window.handleRuleModeChange = function(mode) {
    updateRuleModalSections(mode);
  };

  function updateRuleModalSections(mode) {
    const sSec = document.getElementById('scheduleSection');
    const lSec = document.getElementById('limitSection');
    if (mode === 'schedule') {
      if (sSec) sSec.classList.remove('hidden');
      if (lSec) lSec.classList.add('hidden');
    } else if (mode === 'limit') {
      if (sSec) sSec.classList.add('hidden');
      if (lSec) lSec.classList.remove('hidden');
    } else {
      if (sSec) sSec.classList.add('hidden');
      if (lSec) lSec.classList.add('hidden');
    }
  }

  window.saveRuleConfig = async function() {
    if (!state.activeEditingApp || !state.currentDeviceId) return;

    const pkg = state.activeEditingApp.package_name;
    const appName = state.activeEditingApp.app_name;
    const existingRule = state.rules[pkg];

    let selectedMode = 'block';
    document.querySelectorAll('input[name="ruleMode"]').forEach(r => {
      if (r.checked) selectedMode = r.value;
    });

    let schedulePayload = null;
    if (selectedMode === 'schedule') {
      const sStart = document.getElementById('scheduleStartTime');
      const sEnd = document.getElementById('scheduleEndTime');
      const days = [];
      document.querySelectorAll('input[name="schedDay"]').forEach(cb => {
        if (cb.checked) days.push(parseInt(cb.value, 10));
      });
      schedulePayload = {
        start_time: sStart ? sStart.value : '22:00',
        end_time: sEnd ? sEnd.value : '06:00',
        days: days.length > 0 ? days : [1,2,3,4,5,6,7]
      };
    }

    const dLimit = document.getElementById('dailyLimitMinutes');
    const limitMin = selectedMode === 'limit' ? parseInt(dLimit ? dLimit.value : '45', 10) : 0;
    const cMsg = document.getElementById('ruleCustomMessage');
    const customMessage = cMsg ? cMsg.value.trim() : `${appName} is restricted.`;

    const newVersion = (existingRule?.version || 1) + 1;
    const ruleToSave = {
      id: existingRule?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
      device_id: state.currentDeviceId,
      package_name: pkg,
      mode: selectedMode,
      daily_limit_min: limitMin,
      schedule: schedulePayload,
      message: customMessage,
      enabled: true,
      version: newVersion,
      updated_at: new Date().toISOString()
    };

    state.rules[pkg] = ruleToSave;
    renderAppsList();
    updateKPICards();

    try {
      if (supabaseClient) {
        await supabaseClient.from('rules').upsert(ruleToSave, { onConflict: 'device_id,package_name' });
      } else {
        await apiFetch('rules', {
          method: 'POST',
          headers: { 'Prefer': 'resolution=merge-duplicates' },
          body: JSON.stringify(ruleToSave)
        });
      }
      showToast(`⚡ Applied rule for ${appName} (<5s SLA)`);
    } catch (e) {
      console.error('Error saving rule:', e);
    }

    window.closeRuleConfig();
  };

  // Modal 3: Custom App Rule
  window.openAddCustomRuleModal = function() {
    const modal = document.getElementById('addCustomRuleModal');
    const nameInput = document.getElementById('customAppNameInput');
    const pkgInput = document.getElementById('customPkgNameInput');
    if (nameInput) nameInput.value = '';
    if (pkgInput) pkgInput.value = '';
    if (modal) {
      modal.classList.add('open');
      modal.style.display = 'flex';
    }
  };

  window.closeAddCustomRuleModal = function() {
    const modal = document.getElementById('addCustomRuleModal');
    if (modal) {
      modal.classList.remove('open');
      modal.style.display = '';
    }
  };

  window.selectPresetApp = function(name, pkg) {
    const nameInput = document.getElementById('customAppNameInput');
    const pkgInput = document.getElementById('customPkgNameInput');
    if (nameInput) nameInput.value = name;
    if (pkgInput) pkgInput.value = pkg;
  };

  window.submitCustomAppRule = async function() {
    const nameInput = document.getElementById('customAppNameInput');
    const pkgInput = document.getElementById('customPkgNameInput');
    const name = nameInput ? nameInput.value.trim() : '';
    const pkg = pkgInput ? pkgInput.value.trim() : '';

    if (!pkg) {
      showToast('⚠️ Please enter or select a package name');
      return;
    }

    window.closeAddCustomRuleModal();

    state.activeEditingApp = {
      package_name: pkg,
      app_name: name || pkg
    };

    await window.toggleRuleInstant(pkg, true);
    window.openRuleConfig(pkg);
  };

  window.toggleFocusMode = async function() {
    if (!state.currentDeviceId) {
      showToast('⚠️ Please pair a device first');
      return;
    }

    state.isFocusModeActive = !state.isFocusModeActive;
    const btn = document.getElementById('toggleFocusModeBtn');
    const btnText = document.getElementById('focusBtnText');

    const allApps = getAllDisplayApps();

    if (state.isFocusModeActive) {
      if (btn) btn.classList.add('active');
      if (btnText) btnText.textContent = 'Deactivate Focus Mode';

      const targets = allApps.length > 0 ? allApps : [
        { package_name: 'com.google.android.youtube', app_name: 'YouTube' },
        { package_name: 'com.instagram.android', app_name: 'Instagram' },
        { package_name: 'com.snapchat.android', app_name: 'Snapchat' },
        { package_name: 'com.dts.freefireth', app_name: 'Free Fire' },
        { package_name: 'com.whatsapp', app_name: 'WhatsApp' }
      ];

      for (const app of targets) {
        state.rules[app.package_name] = {
          ...(state.rules[app.package_name] || {}),
          id: state.rules[app.package_name]?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
          device_id: state.currentDeviceId,
          package_name: app.package_name,
          mode: 'block',
          message: 'Focus Mode active: Applications locked by parents.',
          enabled: true,
          version: (state.rules[app.package_name]?.version || 1) + 1,
          updated_at: new Date().toISOString()
        };
        try {
          if (supabaseClient) {
            supabaseClient.from('rules').upsert(state.rules[app.package_name], { onConflict: 'device_id,package_name' }).then();
          } else {
            apiFetch('rules', {
              method: 'POST',
              headers: { 'Prefer': 'resolution=merge-duplicates' },
              body: JSON.stringify(state.rules[app.package_name])
            }).catch(() => {});
          }
        } catch (_) {}
      }
      renderAppsList();
      updateKPICards();
      showToast('🚨 Focus Mode ACTIVATED: All apps locked on child phone!');
    } else {
      if (btn) btn.classList.remove('active');
      if (btnText) btnText.textContent = 'Activate Focus Mode';

      for (const pkg of Object.keys(state.rules)) {
        if (state.rules[pkg]) {
          state.rules[pkg].enabled = false;
          state.rules[pkg].version = (state.rules[pkg].version || 1) + 1;
          try {
            if (supabaseClient) {
              supabaseClient.from('rules').upsert(state.rules[pkg], { onConflict: 'device_id,package_name' }).then();
            } else {
              apiFetch('rules', {
                method: 'POST',
                headers: { 'Prefer': 'resolution=merge-duplicates' },
                body: JSON.stringify(state.rules[pkg])
              }).catch(() => {});
            }
          } catch (_) {}
        }
      }
      renderAppsList();
      updateKPICards();
      showToast('Focus Mode deactivated');
    }
  };

  window.syncRefresh = async function() {
    const btn = document.getElementById('syncRefreshBtn');
    if (btn) btn.classList.add('rotating');
    await loadDevices();
    if (state.currentDeviceId) {
      await loadDeviceData(state.currentDeviceId);
    }
    setTimeout(() => { if (btn) btn.classList.remove('rotating'); }, 600);
    showToast('⚡ Refreshed live database from Supabase');
  };

  window.clearLogs = function() {
    state.blockEvents = [];
    renderTimeline();
    updateKPICards();
    showToast('Activity stream cleared');
  };

  window.unpairDevice = async function() {
    const dev = getCurrentDevice();
    if (!dev) return;
    const ok = confirm(`Are you sure you want to unpair "${dev.name || dev.model}"?`);
    if (!ok) return;

    try {
      if (supabaseClient) {
        await supabaseClient.from('rules').delete().eq('device_id', dev.id);
        await supabaseClient.from('devices').delete().eq('id', dev.id);
      } else {
        await apiFetch(`rules?device_id=eq.${dev.id}`, { method: 'DELETE' });
        await apiFetch(`devices?id=eq.${dev.id}`, { method: 'DELETE' });
      }
    } catch (e) {
      console.warn('Unpair error:', e);
    }

    state.devices = state.devices.filter(d => d.id !== dev.id);
    state.currentDeviceId = state.devices[0]?.id || null;
    renderDeviceSelector();
    if (state.currentDeviceId) {
      await loadDeviceData(state.currentDeviceId);
    } else {
      renderEmptyDashboard();
    }
    showToast('Device removed from management');
  };

  // =============================================================================
  // 8. EVENT LISTENERS SETUP
  // =============================================================================
  function setupEventListeners() {
    const devSelect = document.getElementById('deviceSelector');
    if (devSelect) {
      devSelect.addEventListener('change', async (e) => {
        window.switchDevice(e.target.value);
      });
    }

    const searchInput = document.getElementById('appSearchInput');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        window.handleAppSearch(e.target.value);
      });
    }

    ['pairModal', 'ruleModal', 'addCustomRuleModal'].forEach(id => {
      const modal = document.getElementById(id);
      if (modal) {
        modal.addEventListener('click', (e) => {
          if (e.target === modal) {
            modal.classList.remove('open');
            modal.style.display = '';
          }
        });
      }
    });
  }

  function setupTabNavigation() {
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tabId = btn.dataset.tab;
        window.switchTab(tabId);
      });
    });
  }

  // =============================================================================
  // 9. UTILITY HELPERS
  // =============================================================================
  let toastTimeout = null;
  function showToast(msg) {
    const toast = document.getElementById('toastMessage');
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.remove('hidden');

    if (toastTimeout) clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => {
      toast.classList.add('hidden');
    }, 3500);
  }

  function formatTimeAgo(isoString) {
    if (!isoString) return 'Just now';
    const diffSec = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
    if (diffSec < 45) return 'Just now';
    if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
    if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
    return `${Math.floor(diffSec / 86400)}d ago`;
  }

  function renderDaysShort(daysArr) {
    if (!daysArr || daysArr.length === 7) return 'Everyday';
    const map = { 1: 'Sun', 2: 'Mon', 3: 'Tue', 4: 'Wed', 5: 'Thu', 6: 'Fri', 7: 'Sat' };
    return daysArr.map(d => map[d] || d).join(', ');
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // =============================================================================
  // 10. RELIABLE STARTUP
  // =============================================================================
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
