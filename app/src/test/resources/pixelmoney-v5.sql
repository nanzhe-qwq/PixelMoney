CREATE TABLE `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `amount` REAL NOT NULL);
CREATE TABLE `ledgers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL);
CREATE TABLE `pending_payments` (`id` TEXT NOT NULL, `amount` REAL NOT NULL, `source` TEXT NOT NULL, `note` TEXT NOT NULL, `isIncome` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE `records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `emoji` TEXT NOT NULL, `note` TEXT NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `ledgerId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`ledgerId`) REFERENCES `ledgers`(`id`) ON UPDATE NO ACTION ON DELETE SET DEFAULT );
CREATE TABLE `recurring_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `emoji` TEXT NOT NULL, `note` TEXT NOT NULL, `frequency` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `lastExecutedDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL);
CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
CREATE INDEX `index_records_ledgerId` ON `records` (`ledgerId`);
INSERT INTO room_master_table (id,identity_hash) VALUES(42, '3bd9d60a5f25933f6bb106ee2bc6d02c');
