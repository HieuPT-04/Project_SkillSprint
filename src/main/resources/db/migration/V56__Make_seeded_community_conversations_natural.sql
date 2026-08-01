-- V55 established the volume of the demo community.  This pass removes the
-- remaining template-like copy without changing learner-created conversations.

CREATE FUNCTION v56_v28_uuid(seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5('skillsprint-v28:' || seed), 1, 8) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 9, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 13, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 17, 4) || '-' ||
        substr(md5('skillsprint-v28:' || seed), 21, 12)
    )::uuid;
$$;

WITH ordered_messages AS (
    SELECT ordinal,
           ((ordinal - 1) / 6 + 1)::INTEGER AS room_message_no,
           ((ordinal - 1) % 6 + 1)::INTEGER AS room_no
    FROM generate_series(1, 150) AS sequence(ordinal)
), seeded_messages AS (
    SELECT ordinal,
           (CASE room_no
                WHEN 1 THEN (ARRAY[
                    'Mình vừa chuyển phần kiểm tra quyền vào interceptor, endpoint nhìn gọn hơn nhiều.',
                    'Có ai từng gặp vòng lặp bean khi tách service không? Mình đang lần theo dependency.',
                    'Mình thử dùng record cho DTO response, đọc code đỡ dài hơn hẳn.',
                    'Phần transaction này mình tách nhỏ ra rồi mới viết test cho case rollback.',
                    'Hôm nay mình mới hiểu rõ vì sao không nên trả entity thẳng từ controller.',
                    'Mình đang rà lại validation cho request tạo tài khoản, có field nào hay bị quên không?',
                    'Sau khi bật log SQL, mình phát hiện một chỗ đang query lặp trong vòng lặp.',
                    'Mình vừa đổi exception chung thành lỗi có mã, phía frontend dễ xử lý hơn.',
                    'Ai có ví dụ dễ hiểu về optimistic locking cho một chức năng đơn giản không?',
                    'Mình đang thử viết integration test cho luồng đăng nhập thay vì chỉ mock service.',
                    'Phần phân trang mình đã để sort cố định để kết quả test không bị nhảy.',
                    'Mình vừa sửa N+1 query bằng fetch join, thời gian trả danh sách giảm rõ.',
                    'Có nên đặt mapping DTO ở service hay tạo mapper riêng nhỉ? Mình đang cân nhắc.',
                    'Mình bị nhầm giữa 401 và 403 lúc test API, giờ đã ghi lại ví dụ để nhớ.',
                    'Hôm nay mình refactor package theo feature, tìm code nhanh hơn cấu trúc cũ.',
                    'Mình đang đọc về idempotency key cho thanh toán, case retry khá đáng lưu ý.',
                    'Một test của mình fail vì timezone; cuối cùng nguyên nhân là LocalDateTime.',
                    'Mình vừa thêm index cho cột tìm kiếm và muốn kiểm tra lại bằng explain analyze.',
                    'Có ai dùng Testcontainers với Postgres chưa? Mình muốn test sát môi trường hơn.',
                    'Mình đang tách cấu hình dev và prod để tránh vô tình bật log nhạy cảm.',
                    'Bài hôm nay nhắc mình rằng tên method rõ ràng tiết kiệm thời gian review thật.',
                    'Mình vừa thêm constraint ở database cho dữ liệu bắt buộc, an tâm hơn chỉ validate ở API.',
                    'Khi debug cache, mình thấy cần ghi rõ lúc nào dữ liệu bị invalidate.',
                    'Mình đang làm lại endpoint upload theo presigned URL, luồng an toàn hơn nhiều.',
                    'Ai rảnh xem giúp mình cách đặt boundary cho module notification với?'
                ])[room_message_no]
                WHEN 2 THEN (ARRAY[
                    'Mình đang gom từ vựng theo chủ đề công sở, học xong có ví dụ nên nhớ lâu hơn.',
                    'Part 3 mình hay mất dấu người nói thứ hai, mọi người có mẹo nào không?',
                    'Hôm nay mình nghe một đoạn ngắn ba lần rồi tự tóm tắt bằng tiếng Anh.',
                    'Mình vừa đổi cách ôn từ: đặt câu thay vì chỉ nhìn nghĩa tiếng Việt.',
                    'Có bạn nào có lịch luyện Reading 30 phút mỗi ngày mà không bị quá tải không?',
                    'Mình hay sai câu điều kiện, đang làm bảng phân biệt từng dạng để ôn lại.',
                    'Lúc làm đề mình sẽ gạch keyword trước, tốc độ đọc đỡ bị chậm hơn.',
                    'Mình vừa thử shadowing 10 phút và nhận ra phát âm nối âm của mình còn yếu.',
                    'Hôm nay mình học được vài cụm dùng trong email xin lịch hẹn, khá thực tế.',
                    'Mình đang phân vân nên chữa từng câu sai ngay hay làm hết một part rồi mới chữa.',
                    'Có ai biết nguồn nghe giọng Anh-Anh chậm, chủ đề công việc không?',
                    'Mình đặt mục tiêu mỗi ngày năm từ nhưng phải dùng được trong một câu.',
                    'Bài đọc hôm nay có nhiều từ nối, nhận ra chúng giúp mình đoán ý nhanh hơn.',
                    'Mình vừa ghi âm lại phần giới thiệu bản thân để nghe lỗi ngữ điệu.',
                    'Mình muốn ôn TOEIC theo lỗi sai cá nhân thay vì làm đề liên tục.',
                    'Phần điền từ mình thường xem loại từ trước rồi mới nhìn nghĩa câu.',
                    'Hôm qua mình làm sai vì đọc lướt từ phủ định; từ nay sẽ khoanh chúng trước.',
                    'Mình vừa lập một list collocation hay gặp trong chủ đề tuyển dụng.',
                    'Có ai luyện Speaking một mình bằng cách tự hỏi - tự trả lời không?',
                    'Mình thấy đọc transcript sau khi nghe giúp nhận ra chỗ tai mình bỏ sót.',
                    'Tối nay mình định chữa lại một đề cũ thay vì mở đề mới.',
                    'Mình đang thử ghi chú bằng tiếng Anh thật ngắn để quen tư duy trực tiếp.',
                    'Bài email hôm nay làm mình để ý cách mở đầu lịch sự mà vẫn tự nhiên.',
                    'Mình vừa hoàn thành một mini test và sẽ thống kê lỗi theo dạng câu.',
                    'Ai có mẹo giữ tập trung khi nghe đoạn dài thì chia sẻ giúp mình nhé.'
                ])[room_message_no]
                WHEN 3 THEN (ARRAY[
                    'Mình vừa tách một component form thành các phần nhỏ, phần validate dễ đọc hơn nhiều.',
                    'Có ai từng bị useEffect chạy lại vì object dependency không? Mình đang debug chỗ này.',
                    'Hôm nay mình thử Server Component cho phần chỉ đọc dữ liệu, bundle nhẹ hơn một chút.',
                    'Mình đang cân nhắc giữ filter trên URL hay trong state; case share link khá quan trọng.',
                    'Sau khi thêm loading skeleton, cảm giác trang đỡ bị khựng hơn hẳn.',
                    'Mình vừa phát hiện key của list dùng index nên UI bị lệch khi xoá item.',
                    'Có cách nào tổ chức custom hook cho module lớn mà không bị khó tìm không?',
                    'Mình đang thử React Hook Form và thấy phần error message nhất quán hơn.',
                    'Phần modal này mình chuyển portal rồi, z-index bớt đau đầu hơn trước.',
                    'Mình vừa tối ưu ảnh bằng next/image nhưng cần kiểm tra lại layout shift.',
                    'Hôm nay mình học cách dùng suspense cho trạng thái loading từng khu vực.',
                    'Mình bị hydration mismatch vì render thời gian hiện tại ở server, đã tìm ra nguyên nhân.',
                    'Có ai có thói quen đặt folder cho feature Next.js như thế nào không?',
                    'Mình vừa tách API client ra khỏi component để test UI độc lập dễ hơn.',
                    'Phần search này debounce xong đỡ gửi request liên tục hẳn.',
                    'Mình đang thử Zustand cho state nhỏ, chưa chắc có cần Redux hay không.',
                    'Mình thấy viết empty state tử tế giúp luồng UX rõ hơn nhiều.',
                    'Hôm nay mình sửa lỗi form mất dữ liệu khi chuyển tab bằng cách giữ state ở parent.',
                    'Có ai từng kiểm tra accessibility cho dropdown chưa? Mình đang xử lý keyboard navigation.',
                    'Mình vừa thêm optimistic update cho nút thích bài viết, cảm giác phản hồi tốt hơn.',
                    'Phần table này mình đang tính virtualize vì dữ liệu thực tế dài hơn dự kiến.',
                    'Mình thử dark mode bằng CSS variables, đổi theme không còn phải sửa nhiều class.',
                    'Mình vừa ghi lại convention đặt props để lúc review đỡ phải đoán ý nhau.',
                    'Có ai xử lý upload progress trong Next.js app router rồi cho mình xin hướng đi?',
                    'Mình đang làm lại responsive cho màn nhỏ, nhiều chỗ cần ưu tiên thông tin hơn đẹp.'
                ])[room_message_no]
                WHEN 4 THEN (ARRAY[
                    'Mình vừa giải xong bài two pointers, vẽ tay hai con trỏ trước giúp đỡ rối hơn.',
                    'Bài hôm nay dùng hash map khá gọn nhưng mình vẫn muốn hiểu trade-off bộ nhớ.',
                    'Có ai có cách nhớ khi nào nên nghĩ tới binary search trên đáp án không?',
                    'Mình đang làm lại DFS bằng iterative để hiểu rõ stack hoạt động thế nào.',
                    'Hôm nay mình sai vì quên case mảng rỗng, đã thêm vào checklist trước khi submit.',
                    'Mình vừa so sánh merge sort với quick sort trên dữ liệu gần sắp xếp.',
                    'Có một bài string mình giải được nhưng complexity chưa đẹp, muốn nghe hướng khác.',
                    'Mình đang học prefix sum và thấy nó hữu ích hơn mình tưởng trong bài query.',
                    'Bài graph này mình bị lẫn giữa visited và distance, giờ đã tách chúng rõ hơn.',
                    'Mình vừa viết lại recursion tree cho bài backtracking để theo dõi nhánh cắt.',
                    'Có mẹo nào nhận ra greedy có chứng minh được hay chỉ là trực giác không?',
                    'Hôm nay mình thử dùng monotonic stack cho bài nhiệt độ, khá thú vị.',
                    'Mình đang luyện mô tả ý tưởng trước khi code để đỡ sửa giữa chừng.',
                    'Bài linked list này làm mình nhớ phải giữ reference node trước khi đổi next.',
                    'Mình vừa phát hiện bài này thực ra là shortest path trên state chứ không phải BFS thường.',
                    'Có ai biết nguồn bài SQL window function theo độ khó tăng dần không?',
                    'Mình đang tập viết test case nhỏ cho thuật toán thay vì chỉ chạy sample.',
                    'Hôm nay mình đọc lại proof của dynamic programming, đỡ học thuộc công thức hơn.',
                    'Mình vừa tối ưu bài đếm tần suất bằng array vì domain ký tự đã biết trước.',
                    'Có một edge case overflow mình bỏ sót, may mà test lớn bắt được.',
                    'Mình đang thử giải cùng một bài bằng hai cách để so sánh độ rõ ràng.',
                    'Bài heap hôm nay giúp mình phân biệt rõ top K với việc sort toàn bộ.',
                    'Mình muốn luyện cách đặt tên state trong DP sao cho lúc xem lại vẫn hiểu.',
                    'Có ai hay ghi lại pattern bài tập bằng sơ đồ không? Mình đang thử cách đó.',
                    'Tối nay mình sẽ làm lại bài sai tuần trước trước khi mở bài mới.'
                ])[room_message_no]
                WHEN 5 THEN (ARRAY[
                    'Mình vừa kết thúc phiên 25 phút đầu ngày, mở task nhỏ giúp bắt đầu dễ hơn.',
                    'Hôm nay mình để điện thoại ở phòng khác và không bị ngắt quãng như mọi khi.',
                    'Mình đang thử ghi đúng ba việc quan trọng thay vì viết todo list quá dài.',
                    'Buổi chiều khó tập trung nên mình chuyển sang ôn bài cũ cho nhẹ đầu.',
                    'Mình vừa nghỉ năm phút nhưng không mở mạng xã hội, quay lại làm nhanh hơn.',
                    'Có ai dùng nhạc không lời khi code không? Mình đang tìm playlist ít gây xao nhãng.',
                    'Hôm nay mình dừng đúng giờ để tối còn sức học tiếp, thấy hiệu quả hơn cố quá lâu.',
                    'Mình đang thử chia task theo đầu ra cụ thể, ví dụ xong một trang note thay vì ngồi hai tiếng.',
                    'Phiên vừa rồi bị gián đoạn nên mình viết lại bước tiếp theo trước khi đứng dậy.',
                    'Mình vừa tổng kết tuần và thấy số giờ chưa cao nhưng nhịp đều hơn tuần trước.',
                    'Có bạn nào có cách quay lại việc đang làm sau khi họp xong không?',
                    'Mình đang tập đóng tab không liên quan trước mỗi phiên học.',
                    'Hôm nay mình dùng timer 50/10 cho phần đọc dài, hợp hơn 25/5.',
                    'Mình vừa hoàn thành việc khó nhất trước bữa trưa nên tâm trạng nhẹ hẳn.',
                    'Mình thấy viết một dòng mục tiêu trước phiên học giúp ít trôi sang việc khác.',
                    'Có ai từng thử pomodoro theo nhóm online chưa? Mình muốn có thêm cam kết.',
                    'Hôm nay mình bỏ qua một phiên bị lỡ thay vì cố bù dồn vào buổi tối.',
                    'Mình đang thử chuẩn bị sẵn tài liệu từ tối để sáng mở máy là bắt đầu được.',
                    'Sau vài ngày, mình nhận ra nghỉ ngắn đúng cách quan trọng hơn cố ngồi lâu.',
                    'Mình vừa dọn bàn làm việc, nhìn gọn hơn nên bớt muốn trì hoãn.',
                    'Buổi sáng mình dành 10 phút xem lại kế hoạch, tránh đến trưa mới biết mình làm gì.',
                    'Mình đang để task khó thành bước đầu thật nhỏ để không ngại bắt tay vào.',
                    'Hôm nay mình kết thúc phiên bằng ghi chú chỗ đang dở, mai quay lại nhanh hơn.',
                    'Có ai có mẹo không bị cuốn vào email giữa giờ tập trung không?',
                    'Mình vừa thử thống kê thời gian thực làm thay vì thời gian ngồi trước máy.'
                ])[room_message_no]
                ELSE (ARRAY[
                    'Mình vừa hoàn thiện phần giới thiệu dự án trong CV, đang rút gọn để người đọc nắm ý nhanh.',
                    'Có ai chuẩn bị thực tập muốn cùng luyện trả lời câu hỏi giới thiệu bản thân không?',
                    'Hôm nay mình nhận feedback về portfolio: nên cho thấy quyết định kỹ thuật chứ không chỉ ảnh.',
                    'Mình đang chia roadmap tháng này theo từng kỹ năng nhỏ để theo dõi được tiến độ.',
                    'Mình vừa tham gia review đồ án và ghi lại vài lỗi trình bày khá hay gặp.',
                    'Có bạn nào muốn lập nhóm nhỏ ôn phỏng vấn SQL cuối tuần không?',
                    'Mình đang sửa mô tả dự án theo công thức vấn đề - cách làm - kết quả, dễ đọc hơn hẳn.',
                    'Hôm nay mình học được cách hỏi feedback cụ thể thay vì chỉ hỏi chung là code ổn không.',
                    'Mình vừa cập nhật mục tiêu tuần trên SkillSprint, nhìn lại thấy còn một phần cần ưu tiên.',
                    'Có ai đã đi thực tập có thể chia sẻ cách ghi lại việc làm hằng ngày không?',
                    'Mình đang tập demo dự án trong ba phút để chuẩn bị buổi bảo vệ.',
                    'Hôm nay mình đọc lại JD và nhận ra cần bổ sung ví dụ về teamwork trong CV.',
                    'Mình vừa hoàn thành một mini project, đang nghĩ cách viết README cho người khác chạy được.',
                    'Có ai hay dùng checklist trước buổi phỏng vấn không? Mình muốn tham khảo.',
                    'Mình đang tìm bạn review phần tiếng Anh trong portfolio, đổi góc nhìn chắc sẽ hữu ích.',
                    'Hôm nay mình thử chia mục tiêu lớn thành milestone theo tuần, đỡ bị mơ hồ hơn.',
                    'Mình vừa xem lại roadmap và quyết định bỏ bớt một chủ đề để làm sâu phần quan trọng.',
                    'Có ai có kinh nghiệm trình bày trade-off kỹ thuật cho người không chuyên không?',
                    'Mình đang chuẩn bị câu chuyện về một lỗi khó từng sửa để dùng khi phỏng vấn.',
                    'Hôm nay mình xin được feedback rất cụ thể về slide, sửa xong nhìn gọn hơn nhiều.',
                    'Mình vừa ghép các bài học nhỏ thành kế hoạch ôn ba tuần trước kỳ thi.',
                    'Có ai muốn cùng check-in mục tiêu vào tối chủ nhật để giữ nhịp không?',
                    'Mình đang cân nhắc chọn đề tài có dữ liệu thật cho project cuối kỳ.',
                    'Hôm nay mình cập nhật thành quả vào portfolio thay vì để dồn cuối tháng.',
                    'Mình vừa lập danh sách câu hỏi cần hỏi mentor ở buổi gặp tới.'
                ])[room_message_no]
            END)
            || ' ' ||
            (ARRAY[
                'Ai đã gặp tình huống tương tự thì kể mình nghe với.',
                'Mình sẽ quay lại báo kết quả sau khi thử thêm.',
                'Góc nhìn khác cũng rất đáng để mình tham khảo.',
                'Cảm ơn mọi người trước nếu có gợi ý cụ thể.',
                'Mình muốn hiểu kỹ lý do trước khi chốt cách làm.',
                'Có ví dụ nhỏ minh hoạ thì càng dễ theo dõi.',
                'Mình sẽ lưu lại ý hay để lần sau áp dụng.',
                'Hy vọng bạn nào đang vướng phần này cũng thấy hữu ích.',
                'Tối nay mình sẽ dành thêm thời gian kiểm tra lại.',
                'Mình đang ưu tiên cách làm bền vững hơn là nhanh nhất.',
                'Nếu có tài liệu phù hợp, cho mình xin link nhé.',
                'Mình sẽ thử trên một case nhỏ trước.',
                'Nhờ mọi người góp ý giúp mình tránh bỏ sót case quan trọng.',
                'Có lẽ trao đổi cùng nhau sẽ ra hướng tốt hơn.',
                'Mình ghi lại ở đây để cuối tuần xem lại tiến độ.',
                'Làm xong mình sẽ chia sẻ phần rút ra được.',
                'Mình cũng muốn nghe cách mọi người thường bắt đầu.',
                'Nếu cách này chưa ổn, mình sẵn sàng thử hướng khác.',
                'Cảm ơn cả phòng, đọc chia sẻ của mọi người có động lực hơn.',
                'Mình sẽ cập nhật khi có số liệu hoặc ví dụ rõ hơn.',
                'Đây là phần mình muốn cải thiện trong tuần này.',
                'Mình nghĩ ghi rõ bối cảnh sẽ giúp tìm lời giải đúng hơn.',
                'Ai rảnh có thể xem nhanh giúp mình không?',
                'Mình sẽ tổng hợp lại các ý chính sau buổi này.',
                'Hy vọng lần thử tới sẽ mượt hơn.',
                'Nếu có lỗi mới mình sẽ ghi lại bước tái hiện.',
                'Mình muốn giữ nhịp đều thay vì làm dồn.',
                'Đoạn này xong mình sẽ chuyển sang phần thực hành.',
                'Cùng cố gắng nhé.'
            ])[((ordinal - 1) % 29) + 1] AS content
    FROM ordered_messages
)
UPDATE community_chat_messages message
SET raw_content = seeded.content,
    masked_content = seeded.content
FROM seeded_messages seeded
WHERE message.message_id = v56_v28_uuid('message:' || seeded.ordinal);

WITH seeded_comments AS (
    SELECT ordinal,
           (ARRAY[
               'Mình thích cách bạn nêu rõ chỗ đang vướng, nhờ vậy góp ý cũng cụ thể hơn.',
               'Nếu là mình, mình sẽ thử làm một ví dụ nhỏ trước khi áp dụng cho toàn bộ bài.',
               'Điểm này khá đúng với trải nghiệm của mình; chia nhỏ bước xử lý làm đỡ nản hơn.',
               'Bạn có thể giữ lại ảnh hoặc kết quả trước và sau để lần ôn sau dễ nhớ.',
               'Mình từng bỏ qua giả định ban đầu nên sửa sai hướng, giờ luôn kiểm tra phần đó trước.',
               'Cách bạn ghi tiến độ khá thực tế, đọc xong mình cũng muốn thử trong tuần này.',
               'Phần giải thích này làm mình hiểu vì sao không nên chỉ nhìn kết quả cuối cùng.',
               'Mình đồng ý, có checklist ngắn giúp tránh quên những bước rất cơ bản.',
               'Bạn thử thêm một case biên nhé, đôi khi nó sẽ chỉ ra chỗ cần chỉnh.',
               'Mình cũng đang học phần này và thấy ví dụ của bạn gần với tình huống thực tế.',
               'Cảm ơn bạn đã nói cả phần chưa ổn; những ghi chú đó thường hữu ích nhất.',
               'Mình sẽ lưu ý cách bạn đặt câu hỏi, nó giúp cuộc thảo luận đi đúng trọng tâm.',
               'Nếu có thời gian, bạn thử mô tả lại bằng lời của mình để kiểm tra đã hiểu chưa.',
               'Mình thấy cách làm này phù hợp để quay lại ôn vào cuối tuần.',
               'Bạn đã đi đúng hướng rồi, chỉ cần kiểm tra thêm dữ liệu đầu vào là yên tâm hơn.',
               'Đọc phần này mình nhớ ra một lỗi tương tự từng gặp, lần đó do thiếu bước đối chiếu.',
               'Mình thích việc bạn ưu tiên hiểu nguyên nhân thay vì chỉ tìm cách làm cho chạy.'
           ])[((ordinal - 1) % 17) + 1]
           || ' ' ||
           (ARRAY[
               'Mình sẽ thử áp dụng vào bài đang làm.',
               'Lần sau bạn cập nhật kết quả nhé.',
               'Có thêm ví dụ thì càng dễ hình dung.',
               'Nhờ vậy người mới cũng theo được mạch suy nghĩ.',
               'Mình ghi lại để không quên lúc làm lại.',
               'Cảm ơn bạn đã chia sẻ rất thật.',
               'Mình sẽ kiểm tra lại chỗ này trong note của mình.',
               'Mong là phần sau của bạn cũng thuận lợi.',
               'Góp ý này khiến mình nhìn bài khác đi.',
               'Mình sẽ đem ra thử trong phiên học tới.',
               'Cách trình bày này khá dễ theo dõi.',
               'Mình nghĩ nhiều bạn trong phòng cũng gặp đúng chỗ này.',
               'Đây là một nhắc nhở hay trước khi nộp bài.',
               'Mình sẽ so sánh với cách cũ của mình.',
               'Chúc bạn hoàn thành mục tiêu đã đặt ra.',
               'Nếu cần, mình có thể cùng bạn đối chiếu thêm.',
               'Mình sẽ thử viết lại bước này cho rõ hơn.',
               'Đoạn này đáng để đưa vào checklist.',
               'Mình cũng muốn nghe thêm kết quả sau khi bạn thử.'
           ])[((ordinal - 1) % 19) + 1] AS content
    FROM generate_series(1, 100) AS sequence(ordinal)
)
UPDATE post_comments comment
SET content = seeded.content,
    updated_at = CURRENT_TIMESTAMP
FROM seeded_comments seeded
WHERE comment.comment_id = v56_v28_uuid('comment:' || seeded.ordinal);

DO $$
BEGIN
    IF (SELECT count(DISTINCT raw_content) FROM community_chat_messages
        WHERE message_id IN (SELECT v56_v28_uuid('message:' || n) FROM generate_series(1, 150) AS n)) <> 150
       OR (SELECT count(DISTINCT content) FROM post_comments
           WHERE comment_id IN (SELECT v56_v28_uuid('comment:' || n) FROM generate_series(1, 100) AS n)) <> 100 THEN
        RAISE EXCEPTION 'V56 postcondition failed; seeded community copy is not unique';
    END IF;
END $$;

DROP FUNCTION v56_v28_uuid(TEXT);
