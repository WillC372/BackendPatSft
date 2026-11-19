-- The application is now called Aqua Assistant AI. The demonstration accounts keep their data under their new
-- addresses, and the system profile that named the old knowledge base gets the new name.
UPDATE users SET email = replace(email, '@demo.aquasense', '@demo.aquaassistant')
WHERE demo = TRUE AND email LIKE '%@demo.aquasense';

UPDATE water_profiles SET source = replace(source, 'AquaSense', 'Aqua Assistant')
WHERE source LIKE '%AquaSense%';
