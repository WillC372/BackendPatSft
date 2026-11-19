-- Administrators have no limits: they keep a permanent Premium plan (and the server gives them unlimited AI queries
-- and history).
UPDATE users SET plan = 'PREMIUM', premium_until = NULL WHERE role = 'ADMIN';
