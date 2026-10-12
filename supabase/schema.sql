-- =============================================================================
-- TARGET GUARD — SUPABASE SQL SCHEMA
-- Run this in your Supabase SQL Editor (Dashboard > SQL Editor > New query)
-- =============================================================================

-- 1. Devices Table
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    name TEXT,
    model TEXT,
    android_version TEXT,
    battery_pct INTEGER DEFAULT 100,
    accessibility_enabled BOOLEAN DEFAULT true,
    last_seen_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 2. Pairing Codes Table
CREATE TABLE IF NOT EXISTS public.pairing_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Installed Apps Table
CREATE TABLE IF NOT EXISTS public.installed_apps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    is_system_app BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(device_id, package_name)
);

-- 4. Rules Table
CREATE TABLE IF NOT EXISTS public.rules (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    mode TEXT NOT NULL DEFAULT 'block',
    daily_limit_min INTEGER DEFAULT 0,
    schedule JSONB DEFAULT NULL,
    message TEXT DEFAULT 'This app is blocked by parental controls.',
    enabled BOOLEAN DEFAULT true,
    version INTEGER DEFAULT 1,
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(device_id, package_name)
);

-- 5. Block Events Telemetry Table
CREATE TABLE IF NOT EXISTS public.block_events (
    id BIGSERIAL PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    occurred_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- =============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Enabling Anon read/write access for Parent Dashboard & Target App
-- =============================================================================
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pairing_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.installed_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.block_events ENABLE ROW LEVEL SECURITY;

-- Allow all operations for anon key
CREATE POLICY "Allow anon all on devices" ON public.devices FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on pairing_codes" ON public.pairing_codes FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on installed_apps" ON public.installed_apps FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on rules" ON public.rules FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on block_events" ON public.block_events FOR ALL USING (true) WITH CHECK (true);

-- =============================================================================
-- ENABLE REALTIME ON RELEVANT TABLES
-- =============================================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
ALTER PUBLICATION supabase_realtime ADD TABLE public.rules;
ALTER PUBLICATION supabase_realtime ADD TABLE public.block_events;
