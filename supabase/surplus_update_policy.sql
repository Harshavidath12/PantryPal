-- Apply this in Supabase SQL Editor if edits to surplus_donations fail with
-- "new row violates row-level security policy".
-- Donors may edit their own pending donation, mark courier stages, or complete
-- a pending donation by switching it to self drop-off.

grant select, update on public.surplus_donations to authenticated;

drop policy if exists "Donors can update their own pending surplus donations"
    on public.surplus_donations;
drop policy if exists "Donors can update their own active surplus donations"
    on public.surplus_donations;

create policy "Donors can update their own active surplus donations"
    on public.surplus_donations for update to authenticated
    using (
        auth.uid() = donor_id
        and status in ('PENDING', 'CLAIMED')
    )
    with check (
        auth.uid() = donor_id
        and status in ('PENDING', 'CLAIMED', 'PICKED_UP', 'DROPPED_OFF')
    );
