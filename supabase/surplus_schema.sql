-- PantryPal surplus redistribution feature schema.
-- Run in Supabase SQL Editor. Hub records are public; donations are scoped to
-- the authenticated donor. This assumes Supabase Auth is used by the app.

create table if not exists public.surplus_hubs (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    address text not null,
    distance_km numeric(7,2) not null default 0,
    open_until text not null default 'Contact hub',
    accepted_foods text not null default 'Packaged food',
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.surplus_donations (
    id uuid primary key default gen_random_uuid(),
    donor_id uuid not null references auth.users(id) on delete cascade,
    hub_id uuid not null references public.surplus_hubs(id),
    food_name text not null,
    quantity text not null,
    best_before text not null,
    pickup_window text not null,
    status text not null default 'PENDING'
        check (status in ('PENDING', 'CLAIMED', 'PICKED_UP', 'CANCELLED')),
    created_at timestamptz not null default now()
);

create index if not exists surplus_donations_donor_created_idx
    on public.surplus_donations (donor_id, created_at desc);

alter table public.surplus_hubs enable row level security;
alter table public.surplus_donations enable row level security;

drop policy if exists "Anyone can read active surplus hubs" on public.surplus_hubs;
create policy "Anyone can read active surplus hubs"
    on public.surplus_hubs for select
    using (is_active = true);

drop policy if exists "Donors can read their own surplus donations" on public.surplus_donations;
create policy "Donors can read their own surplus donations"
    on public.surplus_donations for select to authenticated
    using (auth.uid() = donor_id);

drop policy if exists "Donors can create their own surplus donations" on public.surplus_donations;
create policy "Donors can create their own surplus donations"
    on public.surplus_donations for insert to authenticated
    with check (auth.uid() = donor_id);

insert into public.surplus_hubs (name, address, distance_km, open_until, accepted_foods)
values
    ('Colombo Community Kitchen', 'Colombo community area', 1.2, '6:00 PM', 'Produce, dairy, dry goods'),
    ('Green Table NGO', 'Colombo community area', 2.4, '5:00 PM', 'Packaged, dry goods'),
    ('Maple Street Hub', 'Colombo community area', 3.1, '7:00 PM', 'Fresh produce, bakery')
on conflict do nothing;
