-- Enable guest mode for the shared Surplus board.
-- Run in Supabase SQL Editor after deploying the updated Android app.
-- This intentionally makes donation records public and allows any app user
-- to change or delete a pending community donation without an account.

begin;

alter table public.surplus_donations
    alter column donor_id drop not null;

-- Remove account IDs from existing rows because the guest board is public.
update public.surplus_donations
set donor_id = null
where donor_id is not null;

grant select, insert, update, delete on public.surplus_donations to anon, authenticated;

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

commit;
