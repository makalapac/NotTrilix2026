-- NotTrilix 2026 - demo podaci

-- Lozinke su bcrypt (pgcrypto). Demo racuni: ana123 / marko123 / ivana123; admin: admin / admin123
INSERT INTO customers (name, email, phone, password_hash) VALUES
  ('Ana Anić',    'ana.anic@example.com',    '+385 91 111 2222', crypt('ana123',   gen_salt('bf', 10))),
  ('Marko Marić', 'marko.maric@example.com', '+385 98 333 4444', crypt('marko123', gen_salt('bf', 10))),
  ('Ivana Ivić',  'ivana.ivic@example.com',  '+385 95 555 6666', crypt('ivana123', gen_salt('bf', 10)));

INSERT INTO admin_users (username, password_hash, name) VALUES
  ('admin', crypt('admin123', gen_salt('bf', 10)), 'NotTrilix podrška');

INSERT INTO products (category, brand, name, serial_prefix, denomination_eur) VALUES
  ('TELEKOM', 'A1',    'A1 bon',            'A1',  5),
  ('TELEKOM', 'A1',    'A1 bon',            'A1',  10),
  ('TELEKOM', 'A1',    'A1 bon',            'A1',  20),
  ('TELEKOM', 'HT',    'HT bon',            'HT',  5),
  ('TELEKOM', 'HT',    'HT bon',            'HT',  10),
  ('TELEKOM', 'HT',    'HT bon',            'HT',  25),
  ('GAMING',  'STEAM', 'Steam Wallet',      'STM', 10),
  ('GAMING',  'STEAM', 'Steam Wallet',      'STM', 25),
  ('GAMING',  'STEAM', 'Steam Wallet',      'STM', 50),
  ('GAMING',  'SONY',  'PlayStation Store', 'PSN', 20),
  ('GAMING',  'SONY',  'PlayStation Store', 'PSN', 50),
  ('GAMING',  'XBOX',  'Xbox Gift Card',    'XBX', 15),
  ('GAMING',  'XBOX',  'Xbox Gift Card',    'XBX', 30);

-- Zaliha: 8 bonova po proizvodu.
-- Serijski = PREFIX-DENOMINACIJA-NNN, aktivacijski kod = 12 znamenki (deterministicki iz serijskog broja).
INSERT INTO vouchers (product_id, serial_number, code, status)
SELECT s.product_id,
       s.serial_number,
       lpad((abs(hashtextextended(s.serial_number, 0)) % 1000000000000)::text, 12, '0'),
       'AVAILABLE'
FROM (SELECT p.id AS product_id,
             p.serial_prefix || '-' || p.denomination_eur::int || '-' || lpad(g::text, 3, '0') AS serial_number
      FROM products p CROSS JOIN generate_series(1, 8) AS g) s
ORDER BY s.product_id, s.serial_number;

-- Povijest kupnji (razliciti statusi za admin/support scenarije)

-- 1) Ana: A1 10 EUR, web, prije 3 dana - aktivan bon
UPDATE vouchers SET status = 'SOLD', sold_at = now() - interval '3 days' WHERE serial_number = 'A1-10-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur)
SELECT now() - interval '3 days', 'WEB', 'COMPLETED', c.id, v.product_id, v.id, p.denomination_eur
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'A1-10-001' AND c.email = 'ana.anic@example.com';

-- 2) Ana: Steam 25 EUR, web, prije 10 dana - bon vec iskoristen
UPDATE vouchers SET status = 'REDEEMED', sold_at = now() - interval '10 days' WHERE serial_number = 'STM-25-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur)
SELECT now() - interval '10 days', 'WEB', 'COMPLETED', c.id, v.product_id, v.id, p.denomination_eur
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'STM-25-001' AND c.email = 'ana.anic@example.com';

-- 3) Marko: HT 5 EUR, kiosk (POS), prije 2 dana - aktivan bon
UPDATE vouchers SET status = 'SOLD', sold_at = now() - interval '2 days' WHERE serial_number = 'HT-5-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur)
SELECT now() - interval '2 days', 'POS', 'COMPLETED', c.id, v.product_id, v.id, p.denomination_eur
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'HT-5-001' AND c.email = 'marko.maric@example.com';

-- 4) Marko: PlayStation 20 EUR, web, prije 20 dana - iskoristen (i izvan roka za povrat)
UPDATE vouchers SET status = 'REDEEMED', sold_at = now() - interval '20 days' WHERE serial_number = 'PSN-20-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur)
SELECT now() - interval '20 days', 'WEB', 'COMPLETED', c.id, v.product_id, v.id, p.denomination_eur
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'PSN-20-001' AND c.email = 'marko.maric@example.com';

-- 5) Ivana: Xbox 15 EUR, web, prije 5 dana - reklamacija: bon blokiran, iznos vracen na wallet
UPDATE vouchers SET status = 'BLOCKED', sold_at = now() - interval '5 days' WHERE serial_number = 'XBX-15-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur, refunded_at)
SELECT now() - interval '5 days', 'WEB', 'REFUNDED', c.id, v.product_id, v.id, p.denomination_eur, now() - interval '4 days'
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'XBX-15-001' AND c.email = 'ivana.ivic@example.com';

-- 6) Ivana: Steam 50 EUR, web, jucer - aktivan bon
UPDATE vouchers SET status = 'SOLD', sold_at = now() - interval '1 day' WHERE serial_number = 'STM-50-001';
INSERT INTO transactions (created_at, channel, status, customer_id, product_id, voucher_id, amount_eur)
SELECT now() - interval '1 day', 'WEB', 'COMPLETED', c.id, v.product_id, v.id, p.denomination_eur
FROM vouchers v JOIN products p ON p.id = v.product_id, customers c
WHERE v.serial_number = 'STM-50-001' AND c.email = 'ivana.ivic@example.com';

-- Prijave podrske
INSERT INTO support_tickets (created_at, status, customer_id, transaction_id, subject, description, resolution, resolved_at)
SELECT now() - interval '4 days', 'RESOLVED', t.customer_id, t.id,
       'Xbox kod ne radi',
       'Kupac javlja da Xbox prihvaca kod kao nevazeci.',
       'Bon blokiran, iznos vracen na wallet.',
       now() - interval '4 days'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id WHERE v.serial_number = 'XBX-15-001';

INSERT INTO support_tickets (created_at, status, customer_id, transaction_id, subject, description)
SELECT now() - interval '1 day', 'OPEN', t.customer_id, t.id,
       'Nisam dobio kod e-mailom',
       'Kupljeno na kiosku, kupac trazi da mu se kod posalje na e-mail.'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id WHERE v.serial_number = 'HT-5-001';

-- Walleti: pocetna uplata + knjizenja povijesnih kupnji/povrata. Zavrsna stanja: Ana 115, Marko 25, Ivana 50.
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT c.id, now() - interval '12 days', 'TOPUP', 150, 150, NULL, 'Uplata karticom' FROM customers c WHERE c.email = 'ana.anic@example.com';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 125, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'STM-25-001';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 115, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'A1-10-001';

INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT c.id, now() - interval '21 days', 'TOPUP', 50, 50, NULL, 'Uplata karticom' FROM customers c WHERE c.email = 'marko.maric@example.com';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 30, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'PSN-20-001';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 25, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'HT-5-001';

INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT c.id, now() - interval '6 days', 'TOPUP', 100, 100, NULL, 'Uplata karticom' FROM customers c WHERE c.email = 'ivana.ivic@example.com';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 85, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'XBX-15-001';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.refunded_at, 'REFUND', t.amount_eur, 100, t.id, 'Povrat - ' || p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'XBX-15-001';
INSERT INTO wallet_entries (customer_id, created_at, type, amount_eur, balance_after_eur, transaction_id, note)
SELECT t.customer_id, t.created_at, 'PURCHASE', -t.amount_eur, 50, t.id, p.name || ' ' || p.denomination_eur::int || ' €'
FROM transactions t JOIN vouchers v ON v.id = t.voucher_id JOIN products p ON p.id = t.product_id WHERE v.serial_number = 'STM-50-001';

UPDATE customers SET wallet_balance_eur = 115 WHERE email = 'ana.anic@example.com';
UPDATE customers SET wallet_balance_eur = 25  WHERE email = 'marko.maric@example.com';
UPDATE customers SET wallet_balance_eur = 50  WHERE email = 'ivana.ivic@example.com';
