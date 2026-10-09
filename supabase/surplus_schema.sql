-- PantryPal surplus redistribution feature schema.
-- Run in Supabase SQL Editor. Surplus donations are shared public community
-- records so the app can create and display them without a sign-in screen.

create table if not exists public.surplus_hubs (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    address text not null,
    distance_km numeric(7,2) not null default 0,
    open_until text not null default 'Contact hub',
    accepted_foods text not null default 'Packaged food',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    constraint surplus_hubs_name_address_unique unique (name, address)
);

create table if not exists public.surplus_donations (
    id uuid primary key default gen_random_uuid(),
    donor_id uuid references auth.users(id) on delete set null,
    hub_id uuid not null references public.surplus_hubs(id),
    food_name text not null,
    quantity text not null,
    best_before text not null,
    pickup_window text not null,
    status text not null default 'PENDING'
        check (status in ('PENDING', 'CLAIMED', 'PICKED_UP', 'DROPPED_OFF', 'CANCELLED')),
    created_at timestamptz not null default now()
);

alter table public.surplus_donations
    drop constraint if exists surplus_donations_status_check;
alter table public.surplus_donations
    add constraint surplus_donations_status_check
    check (status in ('PENDING', 'CLAIMED', 'PICKED_UP', 'DROPPED_OFF', 'CANCELLED'));

create index if not exists surplus_donations_donor_created_idx
    on public.surplus_donations (donor_id, created_at desc);

alter table public.surplus_hubs enable row level security;
alter table public.surplus_donations enable row level security;

grant select on public.surplus_hubs to anon, authenticated;
grant select, insert, update, delete on public.surplus_donations to anon, authenticated;

drop policy if exists "Anyone can read active surplus hubs" on public.surplus_hubs;
create policy "Anyone can read active surplus hubs"
    on public.surplus_hubs for select
    using (is_active = true);

drop policy if exists "Donors can read their own surplus donations" on public.surplus_donations;
drop policy if exists "Anyone can read community surplus donations" on public.surplus_donations;
create policy "Anyone can read community surplus donations"
    on public.surplus_donations for select to anon, authenticated
    using (true);

drop policy if exists "Donors can create their own surplus donations" on public.surplus_donations;
drop policy if exists "Anyone can create community surplus donations" on public.surplus_donations;
create policy "Anyone can create community surplus donations"
    on public.surplus_donations for insert to anon, authenticated
    with check (donor_id is null);

drop policy if exists "Donors can update their own pending surplus donations" on public.surplus_donations;
drop policy if exists "Donors can update their own active surplus donations" on public.surplus_donations;
drop policy if exists "Anyone can update community surplus donations" on public.surplus_donations;
create policy "Anyone can update community surplus donations"
    on public.surplus_donations for update to anon, authenticated
    using (donor_id is null and status in ('PENDING', 'CLAIMED'))
    with check (donor_id is null and status in ('PENDING', 'CLAIMED', 'PICKED_UP', 'DROPPED_OFF'));

drop policy if exists "Donors can delete their own pending surplus donations" on public.surplus_donations;
drop policy if exists "Anyone can delete pending community surplus donations" on public.surplus_donations;
create policy "Anyone can delete pending community surplus donations"
    on public.surplus_donations for delete to anon, authenticated
    using (donor_id is null and status = 'PENDING');

insert into public.surplus_hubs (name, address, distance_km, open_until, accepted_foods)
values
    ('Colombo Community Kitchen', 'Colombo community area', 1.2, '6:00 PM', 'Produce, dairy, dry goods'),
    ('Green Table NGO', 'Colombo community area', 2.4, '5:00 PM', 'Packaged, dry goods'),
    ('Maple Street Hub', 'Colombo community area', 3.1, '7:00 PM', 'Fresh produce, bakery')
on conflict (name, address) do update set
    distance_km = excluded.distance_km,
    open_until = excluded.open_until,
    accepted_foods = excluded.accepted_foods,
    is_active = true;
