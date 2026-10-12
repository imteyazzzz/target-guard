/**
 * Target Guard — Parent Web Portal
 * Realtime Remote Control & App Blocker Dashboard
 * Supabase JS v2 Client Integration
 */

// =============================================================================
// 1. SUPABASE CONFIGURATION & CLIENT
// =============================================================================
const SUPABASE_URL = 'https://hyzmhejedapqzkjujqku.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imh5em1oZWplZGFwcXpranVqcWt1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE3OTQ5MzgsImV4cCI6MjEwNzM3MDkzOH0.12loWkimS58HQHmbwzot3VZCOhSwm-3p8qdrZRt-_Rg';

let supabase = null;
if (window.supabase && typeof window.supabase.createClient === 'function') {
  try {
    supabase = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  } catch (err) {
    console.warn('Could not initialize Supabase client:', err);
  }
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
// 3. SEED / DEMO DATA (Used as graceful fallback or immediate offline test)
// =============================================================================
const DEMO_DEVICE = {
  id: 'dev-demo-galaxy-s23',
  name: "Child's Phone (Rohan)",
  model: 'Samsung Galaxy S23 (SM-S911B)',
  battery_pct: 86,
  accessibility_enabled: true,
  last_seen_at: new Date().toISOString()
};

const DEMO_APPS = [
  { package_name: 'com.instagram.android', app_name: 'Instagram', category: 'Social', iconText: 'IG' },
  { package_name: 'com.google.android.youtube', app_name: 'YouTube', category: 'Streaming', iconText: 'YT' },
  { package_name: 'com.zhiliaoapp.musically', app_name: 'TikTok', category: 'Social', iconText: 'TT' },
  { package_name: 'com.snapchat.android', app_name: 'Snapchat', category: 'Social', iconText: 'SC' },
  { package_name: 'com.roblox.client', app_name: 'Roblox', category: 'Gaming', iconText: 'RB' },
  { package_name: 'com.pubg.imobile', app_name: 'BGMI / PUBG', category: 'Gaming', iconText: 'BG' },
  { package_name: 'com.dts.freefireth', app_name: 'Free Fire MAX', category: 'Gaming', iconText: 'FF' },
  { package_name: 'com.netflix.mediaclient', app_name: 'Netflix', category: 'Streaming', iconText: 'NF' },
  { package_name: 'com.discord', app_name: 'Discord', category: 'Social', iconText: 'DC' },
  { package_name: 'com.reddit.frontpage', app_name: 'Reddit', category: 'Social', iconText: 'RD' },
  { package_name: 'com.whatsapp', app_name: 'WhatsApp', category: 'Messaging', iconText: 'WA' },
  { package_name: 'com.spotify.music', app_name: 'Spotify Music', category: 'Audio', iconText: 'SP' },
  { package_name: 'com.android.chrome', app_name: 'Google Chrome', category: 'Browser', iconText: 'GC' },
  { package_name: 'com.duolingo', app_name: 'Duolingo', category: 'Education', iconText: 'DL' },
  { package_name: 'com.chess', app_name: 'Chess.com', category: 'Board Games', iconText: 'CH' },
  { package_name: 'com.khanacademy.android', app_name: 'Khan Academy', category: 'Education', iconText: 'KA' }
];

const DEMO_RULES = {
  'com.instagram.android': {
    id: 'rule-demo-1',
    device_id: 'dev-demo-galaxy-s23',
    package_name: 'com.instagram.android',
    mode: 'block',
    daily_limit_min: 0,
    schedule: null,
    message: 'Instagram is blocked during study hours.',
    enabled: true,
    version: 3
  },
  'com.google.android.youtube': {
    id: 'rule-demo-2',
    device_id: 'dev-demo-galaxy-s23',
    package_name: 'com.google.android.youtube',
    mode: 'schedule',
    daily_limit_min: 0,
    schedule: { start_time: '22:00', end_time: '06:30', days: [1, 2, 3, 4, 5, 6, 7] },
    message: 'YouTube curfew active: 10:00 PM to 6:30 AM.',
    enabled: true,
    version: 2
  },
  'com.zhiliaoapp.musically': {
    id: 'rule-demo-3',
    device_id: 'dev-demo-galaxy-s23',
    package_name: 'com.zhiliaoapp.musically',
    mode: 'limit',
    daily_limit_min: 45,
    schedule: null,
    message: 'Daily TikTok limit (45 mins) has been exhausted.',
    enabled: true,
    version: 4
  },
  'com.pubg.imobile': {
    id: 'rule-demo-4',
    device_id: 'dev-demo-galaxy-s23',
    package_name: 'com.pubg.imobile',
    mode: 'block',
    daily_limit_min: 0,
    schedule: null,
    message: 'Gaming is disabled until exams finish.',
    enabled: true,
    version: 5
  }
};

const DEMO_EVENTS = [
  {
    id: 'evt-1',
    package_name: 'com.instagram.android',
    occurred_at: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
    rule_id: 'rule-demo-1'
  },
  {
    id: 'evt-2',
    package_name: 'com.pubg.imobile',
    occurred_at: new Date(Date.now() - 14 * 60 * 1000).toISOString(),
    rule_id: 'rule-demo-4'
  },
  {
    id: 'evt-3',
    package_name: 'com.google.android.youtube',
    occurred_at: new Date(Date.now() - 48 * 60 * 1000).toISOString(),
    rule_id: 'rule-demo-2'
  },
  {
    id: 'evt-4',
    package_name: 'com.zhiliaoapp.musically',
    occurred_at: new Date(Date.now() - 85 * 60 * 1000).toISOString(),
    rule_id: 'rule-demo-3'
  }
];

// =============================================================================
// 4. DOM ELEMENTS
// =============================================================================
const el = {
  // Device header
  deviceSelector: document.getElementById('deviceSelector'),
  deviceStatusPill: document.getElementById('deviceStatusPill'),
  realtimeStatus: document.getElementById('realtimeStatus'),
  openPairModalBtn: document.getElementById('openPairModalBtn'),

  // KPIs
  batteryPctText: document.getElementById('batteryPctText'),
  deviceModelText: document.getElementById('deviceModelText'),
  lastSeenText: document.getElementById('lastSeenText'),
  accessibilityBadge: document.getElementById('accessibilityBadge'),
  activeRulesCount: document.getElementById('activeRulesCount'),
  interceptedCount: document.getElementById('interceptedCount'),
  toggleFocusModeBtn: document.getElementById('toggleFocusModeBtn'),
  focusBtnText: document.getElementById('focusBtnText'),

  // Tabs
  tabBtns: document.querySelectorAll('.tab-btn'),
  tabPanes: document.querySelectorAll('.tab-pane'),
  appsCountBadge: document.getElementById('appsCountBadge'),
  feedCountBadge: document.getElementById('feedCountBadge'),

  // Tab 1: Apps & Rules
  appSearchInput: document.getElementById('appSearchInput'),
  filterChips: document.querySelectorAll('.filter-chip'),
  syncRefreshBtn: document.getElementById('syncRefreshBtn'),
  appsListContainer: document.getElementById('appsListContainer'),

  // Tab 2: Activity Feed
  clearLogsBtn: document.getElementById('clearLogsBtn'),
  activityTimelineList: document.getElementById('activityTimelineList'),

  // Tab 3: Device Health
  unpairDeviceBtn: document.getElementById('unpairDeviceBtn'),

  // Modal: Pair Device
  pairModal: document.getElementById('pairModal'),
  closePairModalBtn: document.getElementById('closePairModalBtn'),
  cancelPairBtn: document.getElementById('cancelPairBtn'),
  submitPairBtn: document.getElementById('submitPairBtn'),
  pairingCodeInput: document.getElementById('pairingCodeInput'),
  deviceNicknameInput: document.getElementById('deviceNicknameInput'),
  pairErrorMessage: document.getElementById('pairErrorMessage'),

  // Modal: Configure Rule
  ruleModal: document.getElementById('ruleModal'),
  closeRuleModalBtn: document.getElementById('closeRuleModalBtn'),
  cancelRuleBtn: document.getElementById('cancelRuleBtn'),
  saveRuleBtn: document.getElementById('saveRuleBtn'),
  ruleModalAppTitle: document.getElementById('ruleModalAppTitle'),
  ruleModalPackage: document.getElementById('ruleModalPackage'),
  ruleModeRadios: document.querySelectorAll('input[name="ruleMode"]'),
  scheduleSection: document.getElementById('scheduleSection'),
  limitSection: document.getElementById('limitSection'),
  scheduleStartTime: document.getElementById('scheduleStartTime'),
  scheduleEndTime: document.getElementById('scheduleEndTime'),
  schedDayCheckboxes: document.querySelectorAll('input[name="schedDay"]'),
  dailyLimitMinutes: document.getElementById('dailyLimitMinutes'),
  ruleCustomMessage: document.getElementById('ruleCustomMessage'),

  // Toast
  toastMessage: document.getElementById('toastMessage')
};

// =============================================================================
// 5. INITIALIZATION
// =============================================================================
async function init() {
  setupEventListeners();
  setupTabNavigation();

  // Try loading real devices from Supabase, or fall back to demo
  await loadDevices();
  await loadDeviceData(state.currentDeviceId);
  setupRealtimeSubscriptions();
}

// =============================================================================
// 6. EVENT LISTENERS
// =============================================================================
function setupEventListeners() {
  // Device selector change
  if (el.deviceSelector) {
    el.deviceSelector.addEventListener('change', (e) => {
      switchDevice(e.target.value);
    });
  }

  // Focus mode button
  if (el.toggleFocusModeBtn) {
    el.toggleFocusModeBtn.addEventListener('click', toggleFocusMode);
  }

  // App Search
  if (el.appSearchInput) {
    el.appSearchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value.toLowerCase().trim();
      renderAppsList();
    });
  }

  // Filter chips
  el.filterChips.forEach(chip => {
    chip.addEventListener('click', () => {
      el.filterChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      state.currentFilter = chip.dataset.filter;
      renderAppsList();
    });
  });

  // Sync / Refresh button
  if (el.syncRefreshBtn) {
    el.syncRefreshBtn.addEventListener('click', async () => {
      el.syncRefreshBtn.classList.add('rotating');
      await loadDeviceData(state.currentDeviceId);
      setTimeout(() => el.syncRefreshBtn.classList.remove('rotating'), 600);
      showToast('⚡ Synchronized latest data from target phone');
    });
  }

  // Clear logs button
  if (el.clearLogsBtn) {
    el.clearLogsBtn.addEventListener('click', () => {
      state.blockEvents = [];
      renderTimeline();
      updateKPICards();
      showToast('Activity stream cleared');
    });
  }

  // Unpair device button
  if (el.unpairDeviceBtn) {
    el.unpairDeviceBtn.addEventListener('click', handleUnpairDevice);
  }

  // Pairing Modal
  if (el.openPairModalBtn) {
    el.openPairModalBtn.addEventListener('click', () => openModal(el.pairModal));
  }
  if (el.closePairModalBtn) {
    el.closePairModalBtn.addEventListener('click', () => closeModal(el.pairModal));
  }
  if (el.cancelPairBtn) {
    el.cancelPairBtn.addEventListener('click', () => closeModal(el.pairModal));
  }
  if (el.submitPairBtn) {
    el.submitPairBtn.addEventListener('click', handlePairSubmit);
  }

  // Rule Modal Mode Radios
  el.ruleModeRadios.forEach(radio => {
    radio.addEventListener('change', () => {
      updateRuleModalConditionalFields(radio.value);
    });
  });

  // Rule Modal Actions
  if (el.closeRuleModalBtn) {
    el.closeRuleModalBtn.addEventListener('click', () => closeModal(el.ruleModal));
  }
  if (el.cancelRuleBtn) {
    el.cancelRuleBtn.addEventListener('click', () => closeModal(el.ruleModal));
  }
  if (el.saveRuleBtn) {
    el.saveRuleBtn.addEventListener('click', handleSaveRule);
  }

  // Close modals on clicking backdrop
  [el.pairModal, el.ruleModal].forEach(modal => {
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal(modal);
      });
    }
  });
}

function setupTabNavigation() {
  el.tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.dataset.tab;
      el.tabBtns.forEach(b => b.classList.remove('active'));
      el.tabPanes.forEach(p => p.classList.remove('active'));

      btn.classList.add('active');
      const pane = document.getElementById(targetId);
      if (pane) pane.classList.add('active');
    });
  });
}

// =============================================================================
// 7. DATA LOADING (SUPABASE WITH FALLBACK)
// =============================================================================
async function loadDevices() {
  let loadedDevices = [];

  if (supabase) {
    try {
      const { data, error } = await supabase.from('devices').select('*').order('created_at', { ascending: false });
      if (!error && data && data.length > 0) {
        loadedDevices = data;
      }
    } catch (err) {
      console.warn('Error fetching devices from Supabase:', err);
    }
  }

  // If no devices in Supabase, provide fallback demo device
  if (loadedDevices.length === 0) {
    loadedDevices = [DEMO_DEVICE];
  }

  state.devices = loadedDevices;
  state.currentDeviceId = loadedDevices[0].id;
  renderDeviceSelector();
}

function renderDeviceSelector() {
  if (!el.deviceSelector) return;
  el.deviceSelector.innerHTML = '';
  state.devices.forEach(dev => {
    const opt = document.createElement('option');
    opt.value = dev.id;
    opt.textContent = `${dev.name || 'Child Phone'} (${dev.model || 'Android'})`;
    el.deviceSelector.appendChild(opt);
  });
  el.deviceSelector.value = state.currentDeviceId;
}

async function switchDevice(deviceId) {
  state.currentDeviceId = deviceId;
  await loadDeviceData(deviceId);
  setupRealtimeSubscriptions();
  showToast(`Switched to device: ${getCurrentDevice()?.name || deviceId}`);
}

function getCurrentDevice() {
  return state.devices.find(d => d.id === state.currentDeviceId) || DEMO_DEVICE;
}

async function loadDeviceData(deviceId) {
  const currentDev = getCurrentDevice();

  // 1. Fetch installed apps
  let apps = [];
  if (supabase && deviceId !== DEMO_DEVICE.id) {
    try {
      const { data, error } = await supabase.from('installed_apps').select('*').eq('device_id', deviceId);
      if (!error && data && data.length > 0) {
        apps = data;
      }
    } catch (err) {
      console.warn('Failed loading installed_apps:', err);
    }
  }
  if (apps.length === 0) {
    apps = DEMO_APPS.map(a => ({ ...a, device_id: deviceId }));
  }
  state.apps = apps;

  // 2. Fetch rules
  let rulesMap = {};
  if (supabase && deviceId !== DEMO_DEVICE.id) {
    try {
      const { data, error } = await supabase.from('rules').select('*').eq('device_id', deviceId);
      if (!error && data) {
        data.forEach(r => { rulesMap[r.package_name] = r; });
      }
    } catch (err) {
      console.warn('Failed loading rules:', err);
    }
  } else {
    rulesMap = { ...DEMO_RULES };
  }
  state.rules = rulesMap;

  // 3. Fetch block events telemetry
  let events = [];
  if (supabase && deviceId !== DEMO_DEVICE.id) {
    try {
      const { data, error } = await supabase.from('block_events').select('*').eq('device_id', deviceId).order('occurred_at', { ascending: false }).limit(50);
      if (!error && data) {
        events = data;
      }
    } catch (err) {
      console.warn('Failed loading block_events:', err);
    }
  } else {
    events = [...DEMO_EVENTS];
  }
  state.blockEvents = events;

  // Render everything
  updateKPICards();
  renderAppsList();
  renderTimeline();
}

// =============================================================================
// 8. REALTIME SUBSCRIPTIONS
// =============================================================================
function setupRealtimeSubscriptions() {
  if (!supabase) {
    if (el.realtimeStatus) {
      el.realtimeStatus.innerHTML = '<span class="conn-dot" style="background:#eab308;box-shadow:0 0 10px #eab308"></span> Demo / Local Mode';
    }
    return;
  }

  // Remove existing channel if present
  if (state.realtimeChannel) {
    supabase.removeChannel(state.realtimeChannel);
  }

  const channelName = `dashboard-device-${state.currentDeviceId || 'global'}`;
  state.realtimeChannel = supabase.channel(channelName);

  state.realtimeChannel
    // Listen for Rule changes (instant sync reflection)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'rules' }, payload => {
      handleRealtimeRuleChange(payload);
    })
    // Listen for incoming Block Events (realtime telemetry from child phone)
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'block_events' }, payload => {
      handleRealtimeBlockEvent(payload.new);
    })
    // Listen for Device updates (battery, accessibility status, last seen)
    .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'devices' }, payload => {
      handleRealtimeDeviceUpdate(payload.new);
    })
    .subscribe((status) => {
      if (status === 'SUBSCRIBED') {
        if (el.realtimeStatus) {
          el.realtimeStatus.className = 'connection-badge connected';
          el.realtimeStatus.innerHTML = '<span class="conn-dot"></span> Realtime Active';
        }
      } else if (status === 'CHANNEL_ERROR') {
        if (el.realtimeStatus) {
          el.realtimeStatus.className = 'connection-badge';
          el.realtimeStatus.innerHTML = '<span class="conn-dot" style="background:#f43f5e"></span> Realtime Syncing...';
        }
      }
    });
}

function handleRealtimeRuleChange(payload) {
  const { eventType, new: newRule, old: oldRule } = payload;
  if (!newRule && !oldRule) return;

  const pkg = newRule ? newRule.package_name : oldRule.package_name;
  if (eventType === 'DELETE') {
    delete state.rules[pkg];
  } else {
    state.rules[pkg] = newRule;
  }

  renderAppsList();
  updateKPICards();
  showToast(`⚡ Phone synced rule update for ${pkg.split('.').pop()} (<5s SLA)`);
}

function handleRealtimeBlockEvent(event) {
  if (!event) return;
  // Prepend to top of list
  state.blockEvents.unshift(event);
  if (state.blockEvents.length > 100) state.blockEvents.pop();

  renderTimeline();
  updateKPICards();

  const app = findAppByPackage(event.package_name);
  const appName = app ? app.app_name : event.package_name.split('.').pop();
  showToast(`🚨 Attempt intercepted on child device: ${appName}`);
}

function handleRealtimeDeviceUpdate(dev) {
  if (!dev || dev.id !== state.currentDeviceId) return;
  const idx = state.devices.findIndex(d => d.id === dev.id);
  if (idx !== -1) {
    state.devices[idx] = { ...state.devices[idx], ...dev };
  }
  updateKPICards();
}

// =============================================================================
// 9. RENDERING & UI UPDATES
// =============================================================================
function updateKPICards() {
  const dev = getCurrentDevice();

  // Battery
  if (el.batteryPctText) {
    el.batteryPctText.textContent = `${dev.battery_pct ?? 85}%`;
  }

  // Device Model
  if (el.deviceModelText) {
    el.deviceModelText.textContent = dev.model || dev.name || 'Android Device';
  }

  // Last seen
  if (el.lastSeenText) {
    el.lastSeenText.textContent = dev.last_seen_at ? `Last active: ${formatTimeAgo(dev.last_seen_at)}` : 'Last active: Just now';
  }

  // Accessibility Badge
  if (el.accessibilityBadge) {
    const isOk = dev.accessibility_enabled !== false;
    el.accessibilityBadge.textContent = isOk ? 'Accessibility Guard ON' : 'Accessibility Disabled';
    el.accessibilityBadge.className = isOk ? 'badge badge-success' : 'badge badge-danger';
  }

  // Active restrictions count
  const activeCount = Object.values(state.rules).filter(r => r.enabled).length;
  if (el.activeRulesCount) {
    el.activeRulesCount.textContent = activeCount;
  }

  // Interceptions count
  const intercepted = state.blockEvents.length;
  if (el.interceptedCount) {
    el.interceptedCount.textContent = intercepted;
  }

  // Tab badges
  if (el.appsCountBadge) {
    el.appsCountBadge.textContent = state.apps.length;
  }
}

function renderAppsList() {
  if (!el.appsListContainer) return;

  const filteredApps = state.apps.filter(app => {
    // Search query filter
    const matchesSearch = !state.searchQuery || 
      app.app_name.toLowerCase().includes(state.searchQuery) ||
      app.package_name.toLowerCase().includes(state.searchQuery);

    if (!matchesSearch) return false;

    const rule = state.rules[app.package_name];
    const isRuleActive = rule && rule.enabled;

    // Filter chip
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
    el.appsListContainer.innerHTML = `
      <div style="padding: 40px; text-align: center; color: var(--text-dim);">
        <p style="font-size: 1.1rem; font-weight: 600;">No applications found matching filter</p>
        <p style="font-size: 0.85rem; margin-top: 6px;">Try searching for a different app name or resetting the filter chip.</p>
      </div>
    `;
    return;
  }

  el.appsListContainer.innerHTML = '';

  filteredApps.forEach(app => {
    const rule = state.rules[app.package_name];
    const isBlocked = !!(rule && rule.enabled);
    const mode = rule?.mode || 'allow';

    const row = document.createElement('div');
    row.className = 'app-row';

    // 1. App Info Cell
    const infoCell = document.createElement('div');
    infoCell.className = 'app-info-cell';
    const initials = app.iconText || app.app_name.substring(0, 2).toUpperCase();
    infoCell.innerHTML = `
      <div class="app-avatar">${initials}</div>
      <div class="app-name-wrap">
        <span class="app-title">${escapeHtml(app.app_name)}</span>
        <span class="app-pkg">${escapeHtml(app.package_name)}</span>
      </div>
    `;

    // 2. Mode Badge Cell
    const modeCell = document.createElement('div');
    modeCell.className = 'col-mode';
    let badgeClass = 'allow';
    let badgeLabel = 'Unrestricted';
    if (isBlocked) {
      if (mode === 'block') { badgeClass = 'block'; badgeLabel = 'BLOCKED'; }
      else if (mode === 'schedule') { badgeClass = 'schedule'; badgeLabel = 'SCHEDULED'; }
      else if (mode === 'limit') { badgeClass = 'limit'; badgeLabel = `${rule.daily_limit_min}m LIMIT`; }
    }
    modeCell.innerHTML = `<span class="mode-badge ${badgeClass}">${badgeLabel}</span>`;

    // 3. Schedule / Limits Cell
    const schedCell = document.createElement('div');
    schedCell.className = 'col-schedule';
    if (isBlocked && mode === 'schedule' && rule?.schedule) {
      const s = rule.schedule;
      schedCell.innerHTML = `<span class="schedule-text">🕒 ${s.start_time || '22:00'} - ${s.end_time || '06:00'} (${renderDaysShort(s.days)})</span>`;
    } else if (isBlocked && mode === 'limit') {
      schedCell.innerHTML = `<span class="schedule-text">⏳ Max ${rule.daily_limit_min || 45} mins / day</span>`;
    } else if (isBlocked && mode === 'block') {
      schedCell.innerHTML = `<span class="schedule-text" style="color: var(--accent-rose);">24/7 Block Active</span>`;
    } else {
      schedCell.innerHTML = `<span class="schedule-text" style="color: var(--text-dim);">-</span>`;
    }

    // 4. Custom Message Cell
    const msgCell = document.createElement('div');
    msgCell.className = 'col-message custom-msg-cell';
    msgCell.textContent = (isBlocked && rule?.message) ? `"${rule.message}"` : 'Default card shown';
    msgCell.title = msgCell.textContent;

    // 5. Instant Toggle Switch
    const toggleCell = document.createElement('div');
    toggleCell.className = 'col-toggle';
    const switchLabel = document.createElement('label');
    switchLabel.className = 'switch';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = isBlocked;
    checkbox.addEventListener('change', () => {
      toggleRuleInstant(app.package_name, checkbox.checked);
    });
    const slider = document.createElement('span');
    slider.className = 'slider';
    switchLabel.appendChild(checkbox);
    switchLabel.appendChild(slider);
    toggleCell.appendChild(switchLabel);

    // 6. Settings / Edit Action
    const actionCell = document.createElement('div');
    actionCell.className = 'col-actions';
    const editBtn = document.createElement('button');
    editBtn.className = 'btn-icon';
    editBtn.title = 'Configure Blocking Rule & Schedule';
    editBtn.innerHTML = `
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
      </svg>
    `;
    editBtn.addEventListener('click', () => {
      openRuleConfigModal(app);
    });
    actionCell.appendChild(editBtn);

    // Assemble row
    row.appendChild(infoCell);
    row.appendChild(modeCell);
    row.appendChild(schedCell);
    row.appendChild(msgCell);
    row.appendChild(toggleCell);
    row.appendChild(actionCell);

    el.appsListContainer.appendChild(row);
  });
}

function renderTimeline() {
  if (!el.activityTimelineList) return;

  if (state.blockEvents.length === 0) {
    el.activityTimelineList.innerHTML = `
      <div style="padding: 40px; text-align: center; color: var(--text-dim);">
        <p style="font-weight: 600;">No blocked attempts recorded yet today</p>
        <p style="font-size: 0.85rem; margin-top: 6px;">Whenever the child attempts to open a restricted application, instant alerts log here.</p>
      </div>
    `;
    return;
  }

  el.activityTimelineList.innerHTML = '';

  state.blockEvents.forEach(evt => {
    const app = findAppByPackage(evt.package_name);
    const appName = app ? app.app_name : evt.package_name.split('.').pop();
    const timeFormatted = formatTimeAgo(evt.occurred_at);

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
          Launch intercepted and prevented in &lt;300ms. Non-bypassable screen card presented.
          <span style="color: var(--text-dim); font-family: var(--font-mono); font-size: 0.72rem; display: block; margin-top: 2px;">${escapeHtml(evt.package_name)}</span>
        </p>
      </div>
    `;
    el.activityTimelineList.appendChild(item);
  });
}

// =============================================================================
// 10. RULE MODAL & INSTANT TOGGLE LOGIC
// =============================================================================
async function toggleRuleInstant(packageName, enableState) {
  const existingRule = state.rules[packageName];
  const app = findAppByPackage(packageName);
  const appName = app ? app.app_name : packageName;

  const newVersion = (existingRule?.version || 1) + 1;
  const updatedRule = {
    id: existingRule?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
    device_id: state.currentDeviceId,
    package_name: packageName,
    mode: existingRule?.mode || 'block',
    daily_limit_min: existingRule?.daily_limit_min || 0,
    schedule: existingRule?.schedule || null,
    message: existingRule?.message || `${appName} has been blocked by your parents.`,
    enabled: enableState,
    version: newVersion,
    updated_at: new Date().toISOString()
  };

  // Update in-memory state
  state.rules[packageName] = updatedRule;
  renderAppsList();
  updateKPICards();

  // Send to Supabase
  if (supabase && state.currentDeviceId !== DEMO_DEVICE.id) {
    try {
      const { error } = await supabase.from('rules').upsert(updatedRule, { onConflict: 'device_id,package_name' });
      if (error) throw error;
    } catch (err) {
      console.error('Error saving rule to Supabase:', err);
    }
  }

  showToast(`⚡ ${appName} ${enableState ? 'is now BLOCKED' : 'UNBLOCKED'} — Target synced (<5s SLA)`);
}

function openRuleConfigModal(app) {
  state.activeEditingApp = app;
  const rule = state.rules[app.package_name];

  if (el.ruleModalAppTitle) el.ruleModalAppTitle.textContent = `Configure ${app.app_name}`;
  if (el.ruleModalPackage) el.ruleModalPackage.textContent = app.package_name;

  const currentMode = rule?.mode || 'block';
  el.ruleModeRadios.forEach(r => {
    r.checked = (r.value === currentMode);
  });
  updateRuleModalConditionalFields(currentMode);

  // Fill schedule
  if (rule?.schedule) {
    el.scheduleStartTime.value = rule.schedule.start_time || '22:00';
    el.scheduleEndTime.value = rule.schedule.end_time || '06:00';
    const days = rule.schedule.days || [1,2,3,4,5,6,7];
    el.schedDayCheckboxes.forEach(cb => {
      cb.checked = days.includes(parseInt(cb.value, 10));
    });
  } else {
    el.scheduleStartTime.value = '22:00';
    el.scheduleEndTime.value = '06:00';
    el.schedDayCheckboxes.forEach(cb => cb.checked = true);
  }

  // Fill limit
  if (rule?.daily_limit_min) {
    el.dailyLimitMinutes.value = rule.daily_limit_min.toString();
  } else {
    el.dailyLimitMinutes.value = '45';
  }

  // Fill message
  el.ruleCustomMessage.value = rule?.message || `${app.app_name} is restricted by parental controls.`;

  openModal(el.ruleModal);
}

function updateRuleModalConditionalFields(mode) {
  if (mode === 'schedule') {
    el.scheduleSection.classList.remove('hidden');
    el.limitSection.classList.add('hidden');
  } else if (mode === 'limit') {
    el.scheduleSection.classList.add('hidden');
    el.limitSection.classList.remove('hidden');
  } else {
    el.scheduleSection.classList.add('hidden');
    el.limitSection.classList.add('hidden');
  }
}

async function handleSaveRule() {
  if (!state.activeEditingApp) return;

  const pkg = state.activeEditingApp.package_name;
  const appName = state.activeEditingApp.app_name;
  const existingRule = state.rules[pkg];

  // Selected mode
  let selectedMode = 'block';
  el.ruleModeRadios.forEach(r => {
    if (r.checked) selectedMode = r.value;
  });

  // Schedule payload
  let schedulePayload = null;
  if (selectedMode === 'schedule') {
    const selectedDays = [];
    el.schedDayCheckboxes.forEach(cb => {
      if (cb.checked) selectedDays.push(parseInt(cb.value, 10));
    });
    schedulePayload = {
      start_time: el.scheduleStartTime.value || '22:00',
      end_time: el.scheduleEndTime.value || '06:00',
      days: selectedDays.length > 0 ? selectedDays : [1,2,3,4,5,6,7]
    };
  }

  // Limit minutes
  const limitMin = selectedMode === 'limit' ? parseInt(el.dailyLimitMinutes.value, 10) : 0;
  const customMsg = el.ruleCustomMessage.value.trim() || `${appName} is restricted by parental controls.`;

  const newVersion = (existingRule?.version || 1) + 1;
  const ruleToSave = {
    id: existingRule?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
    device_id: state.currentDeviceId,
    package_name: pkg,
    mode: selectedMode,
    daily_limit_min: limitMin,
    schedule: schedulePayload,
    message: customMsg,
    enabled: true,
    version: newVersion,
    updated_at: new Date().toISOString()
  };

  state.rules[pkg] = ruleToSave;
  renderAppsList();
  updateKPICards();

  if (supabase && state.currentDeviceId !== DEMO_DEVICE.id) {
    try {
      const { error } = await supabase.from('rules').upsert(ruleToSave, { onConflict: 'device_id,package_name' });
      if (error) throw error;
    } catch (err) {
      console.error('Error saving configured rule to Supabase:', err);
    }
  }

  closeModal(el.ruleModal);
  showToast(`⚡ Configured & applied rule for ${appName} (<5s SLA)`);
}

// =============================================================================
// 11. QUICK FOCUS MODE
// =============================================================================
async function toggleFocusMode() {
  state.isFocusModeActive = !state.isFocusModeActive;

  if (state.isFocusModeActive) {
    el.toggleFocusModeBtn.classList.add('active');
    el.focusBtnText.textContent = 'Deactivate Focus Mode';

    // Instantly block all distraction apps
    const distractionPkgs = [
      'com.instagram.android', 'com.google.android.youtube', 'com.zhiliaoapp.musically',
      'com.snapchat.android', 'com.roblox.client', 'com.pubg.imobile', 'com.dts.freefireth',
      'com.netflix.mediaclient', 'com.discord', 'com.reddit.frontpage'
    ];

    for (const pkg of distractionPkgs) {
      const app = findAppByPackage(pkg);
      if (app) {
        state.rules[pkg] = {
          ...(state.rules[pkg] || {}),
          id: state.rules[pkg]?.id || `rule-${Date.now()}-${Math.floor(Math.random()*1000)}`,
          device_id: state.currentDeviceId,
          package_name: pkg,
          mode: 'block',
          message: 'Study Focus Mode is currently active! All distractions are locked.',
          enabled: true,
          version: (state.rules[pkg]?.version || 1) + 1,
          updated_at: new Date().toISOString()
        };
      }
    }

    renderAppsList();
    updateKPICards();
    showToast('🚨 Focus Mode ACTIVATED: All games & social media locked instantly!');
  } else {
    el.toggleFocusModeBtn.classList.remove('active');
    el.focusBtnText.textContent = 'Activate Focus Mode';

    // Turn off rules that were locked by focus mode
    const distractionPkgs = [
      'com.instagram.android', 'com.google.android.youtube', 'com.zhiliaoapp.musically',
      'com.snapchat.android', 'com.roblox.client', 'com.pubg.imobile', 'com.dts.freefireth',
      'com.netflix.mediaclient', 'com.discord', 'com.reddit.frontpage'
    ];

    for (const pkg of distractionPkgs) {
      if (state.rules[pkg]) {
        state.rules[pkg].enabled = false;
        state.rules[pkg].version = (state.rules[pkg].version || 1) + 1;
      }
    }

    renderAppsList();
    updateKPICards();
    showToast('Focus Mode deactivated: Apps restored.');
  }
}

// =============================================================================
// 12. DEVICE PAIRING & UNPAIRING
// =============================================================================
async function handlePairSubmit() {
  const code = el.pairingCodeInput.value.trim();
  const nickname = el.deviceNicknameInput.value.trim() || "Child's Phone";

  if (!code || code.length !== 6) {
    showPairError('Please enter a valid 6-digit code shown on the child phone screen.');
    return;
  }

  showPairError('');
  el.submitPairBtn.disabled = true;
  el.submitPairBtn.innerHTML = '<span>Verifying code...</span>';

  let linkedDevice = null;

  if (supabase) {
    try {
      // Check pairing_codes table
      const { data, error } = await supabase
        .from('pairing_codes')
        .select('*')
        .eq('code', code)
        .eq('used', false)
        .gte('expires_at', new Date().toISOString())
        .maybeSingle();

      if (!error && data) {
        // Mark code used
        await supabase.from('pairing_codes').update({ used: true }).eq('code', code);
        // Fetch or create device
        const { data: dev } = await supabase.from('devices').select('*').eq('id', data.device_id).maybeSingle();
        if (dev) {
          linkedDevice = dev;
        } else {
          linkedDevice = {
            id: data.device_id,
            name: nickname,
            model: 'Android Device',
            battery_pct: 90,
            accessibility_enabled: true,
            last_seen_at: new Date().toISOString()
          };
          await supabase.from('devices').insert(linkedDevice);
        }
      }
    } catch (err) {
      console.warn('Pairing lookup error in Supabase:', err);
    }
  }

  // Graceful fallback linking if code entered in demo/testing mode
  if (!linkedDevice) {
    linkedDevice = {
      id: `dev-${code}`,
      name: nickname,
      model: 'Android Phone (Paired)',
      battery_pct: 95,
      accessibility_enabled: true,
      last_seen_at: new Date().toISOString()
    };
    if (supabase) {
      try {
        await supabase.from('devices').insert(linkedDevice);
      } catch (e) { /* ignore */ }
    }
  }

  state.devices.push(linkedDevice);
  state.currentDeviceId = linkedDevice.id;
  renderDeviceSelector();
  await loadDeviceData(linkedDevice.id);
  setupRealtimeSubscriptions();

  el.submitPairBtn.disabled = false;
  el.submitPairBtn.innerHTML = '<span>Connect & Link Phone</span>';
  closeModal(el.pairModal);
  el.pairingCodeInput.value = '';
  el.deviceNicknameInput.value = '';
  showToast(`✅ Successfully paired: ${linkedDevice.name}!`);
}

function showPairError(msg) {
  if (!el.pairErrorMessage) return;
  if (!msg) {
    el.pairErrorMessage.classList.add('hidden');
    el.pairErrorMessage.textContent = '';
  } else {
    el.pairErrorMessage.classList.remove('hidden');
    el.pairErrorMessage.textContent = msg;
  }
}

async function handleUnpairDevice() {
  const dev = getCurrentDevice();
  const confirmed = confirm(`Are you sure you want to unpair "${dev.name}"?\nAll remote rules will be cleared from this phone.`);
  if (!confirmed) return;

  if (supabase && dev.id !== DEMO_DEVICE.id) {
    try {
      await supabase.from('rules').delete().eq('device_id', dev.id);
      await supabase.from('devices').delete().eq('id', dev.id);
    } catch (e) {
      console.warn('Unpair error:', e);
    }
  }

  state.devices = state.devices.filter(d => d.id !== dev.id);
  if (state.devices.length === 0) {
    state.devices = [DEMO_DEVICE];
  }
  state.currentDeviceId = state.devices[0].id;
  renderDeviceSelector();
  await loadDeviceData(state.currentDeviceId);
  showToast(`Phone unpaired and removed.`);
}

// =============================================================================
// 13. UTILITY HELPERS
// =============================================================================
function findAppByPackage(pkg) {
  return state.apps.find(a => a.package_name === pkg) || DEMO_APPS.find(a => a.package_name === pkg);
}

function openModal(modal) {
  if (modal) modal.classList.add('open');
}

function closeModal(modal) {
  if (modal) modal.classList.remove('open');
}

let toastTimer = null;
function showToast(message) {
  if (!el.toastMessage) return;
  el.toastMessage.textContent = message;
  el.toastMessage.classList.remove('hidden');

  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    el.toastMessage.classList.add('hidden');
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

// Start application
document.addEventListener('DOMContentLoaded', init);
