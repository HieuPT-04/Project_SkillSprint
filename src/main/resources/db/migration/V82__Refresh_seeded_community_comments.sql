-- Replace repetitive demo comments with short, natural replies.
-- Existing rows are updated in place so moderation status, report links, and post counters remain intact.

WITH v28_replacements AS (
    SELECT ordinal,
           (ARRAY[
               'Hay quá!', 'Chuẩn rồi!', 'Mình lưu lại nhé.', 'Cảm ơn bạn nha.', 'Xịn ghê!',
               'Mình hiểu hơn rồi.', 'Rất hữu ích.', 'Đúng ý mình.', 'Để mình thử.', 'Gọn và dễ hiểu.',
               'Mình cũng gặp vậy.', 'Tip này hay nè.', 'Cùng cố lên!', 'Áp dụng được ngay.', 'Đáng tham khảo.',
               'Mình thích cách này.', 'Quá ổn luôn.', 'Cảm ơn đã chia sẻ.', 'Học được nhiều.', 'Đúng trọng tâm.',
               'Mình note lại rồi.', 'Có động lực hơn.', 'Hay đó bạn.', 'Mình sẽ thử nhé.', 'Rõ hơn hẳn.',
               'Bài này chất lượng.', 'Cách này hợp lý.', 'Mình đồng ý.', 'Thử ngay thôi.', 'Dễ áp dụng ghê.',
               'Cảm ơn nhiều nhé.', 'Mình vừa làm theo.', 'Kinh nghiệm quý nè.', 'Chuẩn bài luôn.', 'Mình cần đúng phần này.',
               'Tóm tắt dễ nhớ.', 'Đọc xong thông hơn.', 'Gợi ý hay quá.', 'Mình bookmark rồi.', 'Giải thích ổn áp.',
               'Có ích thật sự.', 'Đúng lúc mình cần.', 'Làm theo được nè.', 'Mình sẽ xem thêm.', 'Học chung vui hơn.',
               'Phần này rõ ràng.', 'Tư duy hay đó.', 'Nhẹ nhàng mà hiệu quả.', 'Mình thích mẹo này.', 'Cảm ơn bạn nhiều.',
               'Quá thực tế.', 'Mình ghi chú lại.', 'Đã hiểu vấn đề.', 'Hay và súc tích.', 'Mình ủng hộ nhé.',
               'Cách làm thông minh.', 'Thử từ hôm nay.', 'Mình đang cần đây.', 'Cùng tiến bộ nào.', 'Rất đáng thử.',
               'Mình thấy hợp lý.', 'Nghe ổn đấy.', 'Mình sẽ áp dụng.', 'Rất dễ theo dõi.', 'Tối nay thử luôn.',
               'Cảm ơn tip nhé.', 'Mình học thêm được.', 'Đúng là vậy.', 'Có thêm góc nhìn.', 'Mình thích bài này.',
               'Quá tiện luôn.', 'Càng học càng vui.', 'Mình đã lưu bài.', 'Rõ ràng và hữu ích.', 'Hay quá bạn ơi.',
               'Mình hiểu cách làm.', 'Đúng hướng rồi.', 'Tip nhỏ mà hay.', 'Học được một mẹo.', 'Mình làm được rồi.',
               'Cảm ơn đã chỉ.', 'Mình sẽ luyện thêm.', 'Rất đúng luôn.', 'Đọc thấy có động lực.', 'Ý này đáng giá.',
               'Mình thử cách này.', 'Ngắn gọn dễ nhớ.', 'Cảm ơn lời khuyên.', 'Cách này hiệu quả.', 'Mình cùng làm nhé.',
               'Đúng chỗ mình vướng.', 'Mình thấy rõ hơn.', 'Hay ghê!', 'Bài viết ổn quá.', 'Có thêm niềm tin.',
               'Mình theo dõi tiếp.', 'Chia sẻ tuyệt vời.', 'Cảm ơn bạn đã viết.', 'Mình sẽ quay lại xem.', 'Lưu lại để ôn.'
           ])[ordinal] AS content
    FROM generate_series(1, 100) AS seed(ordinal)
)
UPDATE post_comments comment
SET content = replacement.content,
    updated_at = CURRENT_TIMESTAMP
FROM v28_replacements replacement
WHERE comment.comment_id = (
    substr(md5('skillsprint-v28:comment:' || replacement.ordinal), 1, 8) || '-' ||
    substr(md5('skillsprint-v28:comment:' || replacement.ordinal), 9, 4) || '-' ||
    substr(md5('skillsprint-v28:comment:' || replacement.ordinal), 13, 4) || '-' ||
    substr(md5('skillsprint-v28:comment:' || replacement.ordinal), 17, 4) || '-' ||
    substr(md5('skillsprint-v28:comment:' || replacement.ordinal), 21, 12)
)::uuid;

WITH duplicate_seed_replacements(comment_id, content) AS (
    VALUES
        ('58f9fab0-47bf-ea02-7110-990d6cbdf842'::uuid, 'Mình cũng thử rồi.'),
        ('29adcde0-40fb-cc8b-165e-18371700119a'::uuid, 'Phần này rất hay.'),
        ('08fd5735-f5d8-c9c5-1798-07893ce619fc'::uuid, 'Mình sẽ đổi nhịp.'),
        ('1aee49b9-b08f-80c8-9553-5eaff68d8b43'::uuid, 'Bắt đầu nhỏ thôi.'),
        ('c694b4ca-a75c-0c1f-c462-9ac469d47e4d'::uuid, 'Chia chương tiện thật.'),
        ('c670978a-3f15-6a8a-ecc8-dafc19bc5c63'::uuid, 'Mình sẽ duy trì đều.'),
        ('44d24aa8-1485-59f9-429e-4a8d8319e8da'::uuid, 'Chia phiên dễ bắt đầu.'),
        ('247f4ef6-fe00-18dd-445c-4effa0aa67fe'::uuid, 'Giải thích rất rõ.'),
        ('b68c12dc-5cac-74b1-324b-77ac73b424c6'::uuid, 'Cuối tuần mình thử.'),
        ('ac0f4d8b-1a7c-adca-4d2c-b17579cd7a56'::uuid, 'Ví dụ nhỏ dễ hiểu.'),
        ('7fd16ea4-a3f0-bbd5-d8a9-92c89f38b01b'::uuid, 'Ôn theo chương hợp lý.'),
        ('daa44254-0bae-ba51-45e5-c4ab1b2ea577'::uuid, 'Mình quyết tâm hơn.'),
        ('036a54f7-e500-624b-73d1-b68f5f276a8a'::uuid, 'Chúc bạn bứt phá.'),
        ('63b35540-4145-4935-4c9d-c31064e1f01a'::uuid, 'Cách giải thích dễ tiếp thu.'),
        ('3f10e91e-b912-dd29-6c25-a78c7c5b1613'::uuid, 'SQL rất đáng học.'),
        ('562ec9e3-1882-6af2-1e6a-0250d033ef1f'::uuid, 'AI hỗ trợ tốt ghê.'),
        ('e8a7025c-45de-2f3d-60b6-7456ebbac596'::uuid, 'Chào tuần mới nhé.'),
        ('41d5e3ba-2053-d3d3-ca50-4626d57c086f'::uuid, 'Marketplace có nhiều bộ hay.'),
        ('1b5990aa-d5db-8d10-24b1-b1fed89869db'::uuid, 'Mục tiêu nào cũng tới.'),
        ('efadaedc-f74a-10e6-434d-9a0d6bf3eec7'::uuid, 'Giải thích kỹ rất quý.'),
        ('7e266580-79ad-2c63-7f4b-f1021b61e1f3'::uuid, 'Tối ưu SQL đã thật.'),
        ('ebdcd039-b252-2558-7752-1af8bfd96bd4'::uuid, 'Roadmap này ổn áp.'),
        ('a0fdb8e9-c89d-dcb9-559a-81835302ea65'::uuid, 'Năng lượng lên nào.'),
        ('e898b10b-42ec-8ecf-de78-bfbd2824dc17'::uuid, 'Để mình ghé xem.'),
        ('c34d2fe4-40eb-48f0-b54d-c10a52a8abf1'::uuid, 'Bài này truyền cảm hứng.'),
        ('1a84ba5a-4677-4de8-924b-35d89da3381a'::uuid, 'Chào bạn, cùng học nhé.')
)
UPDATE post_comments comment
SET content = replacement.content,
    updated_at = CURRENT_TIMESTAMP
FROM duplicate_seed_replacements replacement
WHERE comment.comment_id = replacement.comment_id;

UPDATE community_posts post
SET comment_count = (
        SELECT count(*)
        FROM post_comments comment
        WHERE comment.post_id = post.post_id
          AND comment.status = 'VISIBLE'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE post.post_id IN (
    SELECT comment.post_id
    FROM post_comments comment
    WHERE comment.comment_id IN (
        SELECT (
            substr(md5('skillsprint-v28:comment:' || ordinal), 1, 8) || '-' ||
            substr(md5('skillsprint-v28:comment:' || ordinal), 9, 4) || '-' ||
            substr(md5('skillsprint-v28:comment:' || ordinal), 13, 4) || '-' ||
            substr(md5('skillsprint-v28:comment:' || ordinal), 17, 4) || '-' ||
            substr(md5('skillsprint-v28:comment:' || ordinal), 21, 12)
        )::uuid
        FROM generate_series(1, 100) AS seed(ordinal)
    )
       OR comment.comment_id IN (
            '58f9fab0-47bf-ea02-7110-990d6cbdf842'::uuid,
            '29adcde0-40fb-cc8b-165e-18371700119a'::uuid,
            '08fd5735-f5d8-c9c5-1798-07893ce619fc'::uuid,
            '1aee49b9-b08f-80c8-9553-5eaff68d8b43'::uuid,
            'c694b4ca-a75c-0c1f-c462-9ac469d47e4d'::uuid,
            'c670978a-3f15-6a8a-ecc8-dafc19bc5c63'::uuid,
            '44d24aa8-1485-59f9-429e-4a8d8319e8da'::uuid,
            '247f4ef6-fe00-18dd-445c-4effa0aa67fe'::uuid,
            'b68c12dc-5cac-74b1-324b-77ac73b424c6'::uuid,
            'ac0f4d8b-1a7c-adca-4d2c-b17579cd7a56'::uuid,
            '7fd16ea4-a3f0-bbd5-d8a9-92c89f38b01b'::uuid,
            'daa44254-0bae-ba51-45e5-c4ab1b2ea577'::uuid,
            '036a54f7-e500-624b-73d1-b68f5f276a8a'::uuid,
            '63b35540-4145-4935-4c9d-c31064e1f01a'::uuid,
            '3f10e91e-b912-dd29-6c25-a78c7c5b1613'::uuid,
            '562ec9e3-1882-6af2-1e6a-0250d033ef1f'::uuid,
            'e8a7025c-45de-2f3d-60b6-7456ebbac596'::uuid,
            '41d5e3ba-2053-d3d3-ca50-4626d57c086f'::uuid,
            '1b5990aa-d5db-8d10-24b1-b1fed89869db'::uuid,
            'efadaedc-f74a-10e6-434d-9a0d6bf3eec7'::uuid,
            '7e266580-79ad-2c63-7f4b-f1021b61e1f3'::uuid,
            'ebdcd039-b252-2558-7752-1af8bfd96bd4'::uuid,
            'a0fdb8e9-c89d-dcb9-559a-81835302ea65'::uuid,
            'e898b10b-42ec-8ecf-de78-bfbd2824dc17'::uuid,
            'c34d2fe4-40eb-48f0-b54d-c10a52a8abf1'::uuid,
            '1a84ba5a-4677-4de8-924b-35d89da3381a'::uuid
       )
);
