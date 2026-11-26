ALTER TABLE hsk_vocabulary
    ADD COLUMN han_viet text,
    ADD COLUMN han_viet_note_vi text,
    ADD COLUMN word_type_vi text,
    ADD COLUMN usage_note_vi text;

CREATE TABLE hsk_vocabulary_examples (
    syllabus varchar(16) NOT NULL,
    level smallint NOT NULL,
    entry_number integer NOT NULL,
    position smallint NOT NULL CHECK (position > 0),
    sentence_zh text NOT NULL,
    pinyin text NOT NULL,
    translation_vi text NOT NULL,
    note_vi text,
    PRIMARY KEY (syllabus, level, entry_number, position),
    FOREIGN KEY (syllabus, level, entry_number)
        REFERENCES hsk_vocabulary ON DELETE CASCADE
);

UPDATE hsk_vocabulary
SET han_viet = 'nhĩ hảo',
    han_viet_note_vi = '你 (nhĩ) nghĩa là “bạn”, 好 (hảo) nghĩa là “tốt”. Ghép lại thành lời chào.',
    word_type_vi = 'Cụm từ',
    usage_note_vi = 'Dùng để chào khi gặp mặt. Khi cần thể hiện sự kính trọng, có thể dùng 您好 (nín hǎo).'
WHERE syllabus = '3.0-2025' AND level = 1 AND entry_number = 147 AND hanzi = '你好';

INSERT INTO hsk_vocabulary_examples
    (syllabus, level, entry_number, position, sentence_zh, pinyin, translation_vi, note_vi)
VALUES
    ('3.0-2025', 1, 147, 1, '你好，我叫明安。', 'Nǐ hǎo, wǒ jiào Míng''ān.', 'Xin chào, tôi tên là Minh An.', NULL),
    ('3.0-2025', 1, 147, 2, '老师，您好！', 'Lǎoshī, nín hǎo!', 'Em chào thầy/cô ạ!', '您 (nín) là cách xưng hô lịch sự tương ứng với 你 (nǐ).');
