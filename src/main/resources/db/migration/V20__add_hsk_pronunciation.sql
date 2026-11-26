ALTER TABLE hsk_vocabulary ADD COLUMN audio_path text;

UPDATE hsk_vocabulary
SET audio_path = '/audio/hsk/3.0-2025/1/' || entry_number || '.m4a'
WHERE syllabus = '3.0-2025' AND level = 1 AND entry_number BETWEEN 1 AND 300;
