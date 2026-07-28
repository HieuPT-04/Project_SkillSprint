-- Upgrade only the three seeded marketplace packs visible in the catalogue.
-- Completed purchases retain their recorded historical price; only the current
-- catalogue and saleable version price become 50,000 Coin.

CREATE FUNCTION v43_seed_uuid(namespace_prefix TEXT, seed TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5(namespace_prefix || ':' || seed), 1, 8) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 9, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 13, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 17, 4) || '-' ||
        substr(md5(namespace_prefix || ':' || seed), 21, 12)
    )::uuid;
$$;

CREATE TEMP TABLE v43_marketplace_question_bank (
    product_no INTEGER NOT NULL,
    step_no INTEGER NOT NULL,
    question_no INTEGER NOT NULL,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    explanation TEXT NOT NULL,
    PRIMARY KEY (product_no, step_no, question_no)
) ON COMMIT DROP;

INSERT INTO v43_marketplace_question_bank VALUES
-- SQL and Database Tuning
(3,1,1,'Mệnh đề nào dùng để lọc các bản ghi trước khi dữ liệu được gom nhóm?','WHERE','HAVING','ORDER BY','DISTINCT','WHERE lọc từng hàng đầu vào; HAVING được áp dụng sau GROUP BY.'),
(3,1,2,'Khi điều kiện có thể nhận giá trị NULL, biểu thức nào kiểm tra đúng giá trị thiếu?','IS NULL','= NULL','== NULL','NULL = NULL','Trong SQL, NULL không so sánh bằng toán tử =; cần dùng IS NULL.'),
(3,1,3,'Kết quả của INNER JOIN là gì?','Chỉ các hàng có khóa khớp ở cả hai bảng','Tất cả hàng của bảng trái','Tất cả hàng của cả hai bảng','Chỉ các hàng không khớp','INNER JOIN chỉ giữ các cặp hàng thỏa điều kiện JOIN.'),
(3,1,4,'Hàm COUNT(cot) xử lý giá trị NULL như thế nào?','Không đếm các giá trị NULL','Đếm mọi hàng kể cả NULL','Trả về NULL khi có NULL','Chỉ đếm các hàng trùng','COUNT với tên cột chỉ đếm giá trị khác NULL.'),
(3,1,5,'Khóa ngoại chủ yếu giúp bảo đảm điều gì?','Tính toàn vẹn tham chiếu giữa các bảng','Tốc độ truy vấn luôn nhanh hơn','Dữ liệu tự động được mã hóa','Mỗi bảng chỉ có một khóa','Foreign key ngăn tham chiếu đến bản ghi cha không tồn tại.'),
(3,2,1,'Chỉ mục B-tree phù hợp nhất với kiểu truy vấn nào?','So sánh bằng và truy vấn theo khoảng','Chỉ tìm kiếm toàn văn','Chỉ dữ liệu JSON','Chỉ cột có giá trị NULL','B-tree hỗ trợ tốt =, <, >, BETWEEN và sắp xếp theo cột chỉ mục.'),
(3,2,2,'Với chỉ mục ghép (department_id, created_at), điều kiện nào tận dụng tốt thứ tự chỉ mục?','department_id = 7 AND created_at >= CURRENT_DATE - INTERVAL ''30 days''','created_at >= CURRENT_DATE - INTERVAL ''30 days''','ORDER BY updated_at','email ILIKE ''%anh%''','Cột đứng đầu department_id cần được ràng buộc để khai thác phần sau hiệu quả.'),
(3,2,3,'EXPLAIN ANALYZE cung cấp thêm thông tin gì so với EXPLAIN?','Kế hoạch thực thi thực tế cùng thời gian và số dòng','Chỉ câu SQL đã chuẩn hóa','Danh sách khóa ngoại','Dữ liệu kết quả của truy vấn','EXPLAIN ANALYZE chạy truy vấn và báo số liệu thực tế của từng node.'),
(3,2,4,'Khi nào covering index có thể giảm truy cập bảng gốc?','Khi các cột cần đọc đều có trong chỉ mục và hệ quản trị có thể index-only scan','Khi bảng không có khóa chính','Khi truy vấn dùng SELECT *','Khi chỉ mục là UNIQUE','Index-only scan có thể trả dữ liệu từ chỉ mục nếu visibility map cho phép.'),
(3,2,5,'Vì sao không nên tạo chỉ mục cho mọi cột?','Chỉ mục làm tăng chi phí ghi và chiếm dung lượng','Chỉ mục làm mất dữ liệu','Chỉ mục cấm dùng JOIN','Chỉ mục làm khóa ngoại vô hiệu','INSERT, UPDATE và DELETE phải duy trì tất cả chỉ mục liên quan.'),
(3,3,1,'Thuộc tính Atomicity trong ACID có nghĩa là gì?','Toàn bộ giao dịch thành công hoặc toàn bộ bị hủy','Mỗi truy vấn luôn chạy song song','Dữ liệu luôn được sao chép','Chỉ có một người dùng hệ thống','Atomicity bảo đảm không tồn tại trạng thái thực hiện dở dang của giao dịch.'),
(3,3,2,'Hiện tượng lost update thường xảy ra khi nào?','Hai giao dịch đọc cùng dữ liệu rồi lần lượt ghi đè kết quả của nhau','Một truy vấn dùng JOIN','Bảng có quá nhiều index','Có dùng khóa chính UUID','Không có cơ chế khóa hoặc mức cô lập phù hợp thì cập nhật sau có thể ghi đè cập nhật trước.'),
(3,3,3,'Lệnh nào kết thúc giao dịch và lưu các thay đổi?','COMMIT','ROLLBACK','SAVEPOINT','EXPLAIN','COMMIT làm các thay đổi trong transaction trở nên bền vững.'),
(3,3,4,'Mức cô lập nào ngăn dirty read theo chuẩn SQL?','READ COMMITTED','READ UNCOMMITTED','AUTOCOMMIT','NO LOCK','READ COMMITTED không cho đọc dữ liệu chưa được giao dịch khác commit.'),
(3,3,5,'Khi gặp deadlock, chiến lược xử lý phù hợp ở tầng ứng dụng là gì?','Rollback giao dịch bị chọn và thử lại có kiểm soát','Bỏ qua lỗi và tiếp tục commit','Tắt toàn bộ khóa','Xóa các bảng liên quan','Hệ quản trị sẽ hủy một giao dịch; ứng dụng cần retry theo chính sách giới hạn.'),
(3,4,1,'Hàm RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) dùng để làm gì?','Xếp hạng lương trong từng phòng ban','Đếm số phòng ban','Gộp toàn bộ lương thành một giá trị','Tạo khóa chính','PARTITION BY chia tập dữ liệu theo phòng ban trước khi xếp hạng.'),
(3,4,2,'UPSERT trong PostgreSQL thường dùng cú pháp nào?','INSERT ... ON CONFLICT ...','UPDATE OR CREATE','MERGE TABLE ONLY','PUT INTO','ON CONFLICT cho phép xử lý trường hợp vi phạm unique constraint khi insert.'),
(3,4,3,'Lợi ích chính của prepared statement là gì?','Giảm rủi ro SQL injection và hỗ trợ tái sử dụng kế hoạch','Tự tạo index cho cột','Tự động commit mọi giao dịch','Thay thế hoàn toàn quyền truy cập','Tham số được truyền tách khỏi chuỗi SQL nên không bị diễn giải thành mã lệnh.'),
(3,4,4,'Partition pruning hoạt động tốt nhất khi nào?','Điều kiện WHERE giới hạn rõ khóa phân vùng','Truy vấn luôn dùng SELECT *','Bảng chỉ có một hàng','Không có index nào','Hệ quản trị có thể bỏ qua các partition không thể chứa dữ liệu thỏa điều kiện.'),
(3,4,5,'Để xóa bản ghi cha khi còn bản ghi con, lựa chọn nào giữ toàn vẹn tham chiếu?','Khai báo ON DELETE CASCADE khi nghiệp vụ cho phép','Xóa cột khóa chính','Tắt foreign key vĩnh viễn','Dùng TRUNCATE ngẫu nhiên','ON DELETE CASCADE chỉ nên dùng khi vòng đời bản ghi con phụ thuộc bản ghi cha.'),
-- English B2 / TOEIC 750+
(4,1,1,'Choose the correct sentence.','She has worked for the company since 2022.','She works for the company since 2022.','She is working for the company since 2022.','She has been work for the company since 2022.','Present perfect is used with since to describe an action continuing until now.'),
(4,1,2,'Complete the sentence: If I ___ more time, I would join the course.','had','have','will have','had had','The second conditional uses past simple in the if-clause for an unreal present situation.'),
(4,1,3,'Choose the correct passive form: The manager will announce the result tomorrow.','The result will be announced by the manager tomorrow.','The result will announce tomorrow.','The result is announcing by the manager tomorrow.','The manager will be announced the result tomorrow.','Future passive uses will be plus past participle.'),
(4,1,4,'Which relative pronoun best completes the sentence: The report ___ I sent yesterday needs revision.','that','where','whose','whom','That can refer to a thing functioning as the object of the relative clause.'),
(4,1,5,'Choose the most natural collocation.','meet a deadline','do a deadline','make a deadline','take a deadline','In professional English, people meet or miss a deadline.'),
(4,2,1,'Choose the correct word form: The proposal was carefully ___.','prepared','preparation','preparing','prepare','A past participle is needed after was to form the passive voice.'),
(4,2,2,'What does the word reliable most nearly mean?','able to be trusted to work well','expensive to maintain','difficult to explain','quick to replace','Reliable describes a person or thing that can be depended on.'),
(4,2,3,'Choose the correct preposition: Please submit the application ___ Friday.','by','at','on','from','By Friday means no later than Friday.'),
(4,2,4,'Which sentence is grammatically correct?','Neither the manager nor the assistants were available.','Neither the manager nor the assistants was available.','Neither manager nor assistants is available.','Neither the manager and the assistants were available.','With neither...nor, the verb commonly agrees with the nearer plural subject assistants.'),
(4,2,5,'Choose the best connector: The system was tested thoroughly; ___, a minor issue remained.','however','therefore','because','unless','However introduces a contrast with the preceding statement.'),
(4,3,1,'In a reading passage, an inference is a conclusion that is ___.','supported by clues rather than stated directly','copied word for word from the text','always an opinion of the reader','unrelated to the main idea','Inference combines evidence in the text with logical reasoning.'),
(4,3,2,'What is the best skimming strategy before a detailed reading?','Read headings, topic sentences and conclusion for the main idea','Translate every word in order','Memorize all numbers first','Ignore the title','Skimming focuses on structure and key sentences to identify the main message.'),
(4,3,3,'Choose the best formal email opening.','Dear Ms. Tran,','Hey Tran!','Hi there, buddy,','To whom it may concern, Tran','Dear plus title and surname is appropriate when the recipient is known.'),
(4,3,4,'Which phrase is most suitable for a polite request?','Could you please send the revised invoice by noon?','Send the invoice now.','You must send me the invoice.','Why have you not sent it?','Could you please is clear and professional without sounding demanding.'),
(4,3,5,'What does despite signal in a sentence?','A contrast between two ideas','A sequence of events','A reason and result','A definition','Despite is followed by a noun phrase and introduces an unexpected contrast.'),
(4,4,1,'Choose the best modal deduction: The lights are off and nobody answers. They ___ be at home.','cannot','must','should','would','Cannot expresses a strong negative deduction from the available evidence.'),
(4,4,2,'Which sentence uses reported speech correctly?','Lan said that she would finish the task that day.','Lan said that she will finish the task today.','Lan said she finish the task that day.','Lan said that would she finish the task.','Would and that day are typical backshifts when reporting a future statement.'),
(4,4,3,'Choose the sentence with correct parallel structure.','The role requires planning, communicating and solving problems.','The role requires to plan, communicating and problem solving.','The role requires planning, to communicate and solve problems.','The role requires plan, communicate and solving problems.','Items in a list should use the same grammatical form.'),
(4,4,4,'In a TOEIC-style notice, which detail most often answers the question Where will the event take place?','The venue or room number','The speaker opinion','The past tense verb','The company slogan','Location questions are answered by a named place, address, floor or room.'),
(4,4,5,'Choose the most concise professional revision.','The meeting has been moved to 9 a.m. on Tuesday.','It is hereby informed that the meeting has got moved to 9 a.m. Tuesday morning.','The meeting, which is important, is moved in a way to Tuesday 9 a.m.','We are moving meeting maybe Tuesday.','Professional writing favors direct wording with a precise time.'),
-- Data structures and algorithms
(5,1,1,'What is the time complexity of binary search on a sorted array?','O(log n)','O(n)','O(n log n)','O(1)','Binary search halves the remaining search interval after each comparison.'),
(5,1,2,'Which data structure follows the Last In First Out principle?','Stack','Queue','Priority queue','Hash table','A stack removes the most recently pushed item first.'),
(5,1,3,'Which operation is typically O(1) amortized for appending to a dynamic array?','Add an element at the end','Insert at the beginning','Search an unsorted element','Sort all elements','Occasional resizing is expensive, but average append cost remains constant amortized.'),
(5,1,4,'What is a stable sorting algorithm expected to preserve?','Relative order of equal keys','Only descending order','Memory usage of O(1)','The original array length','Stability keeps records with equal sort keys in their input order.'),
(5,1,5,'Which structure is most appropriate for checking balanced parentheses?','Stack','Binary search tree','Disjoint set','Circular queue','Push opening symbols and pop when matching closing symbols are encountered.'),
(5,2,1,'Breadth-first search is especially useful for finding what in an unweighted graph?','Shortest path by number of edges','Minimum spanning tree with negative edges','All strongly connected components only','A topological order in any graph','BFS visits vertices by increasing distance from the source.'),
(5,2,2,'Which condition is required for Dijkstra algorithm to be correct?','All edge weights are non-negative','The graph must be complete','The graph must be a tree','Every vertex has equal degree','A negative edge can invalidate a distance already finalized by Dijkstra.'),
(5,2,3,'Topological sorting is defined for which kind of graph?','Directed acyclic graph','Undirected connected graph','Weighted complete graph','Any graph with a cycle','A topological order exists only when directed dependencies contain no cycle.'),
(5,2,4,'What does a visited set prevent during graph traversal?','Processing the same vertex repeatedly','Sorting vertices alphabetically','Adding edge weights','Creating adjacency lists','Marking visited vertices prevents loops from causing repeated exploration.'),
(5,2,5,'Which representation is usually more space-efficient for a sparse graph?','Adjacency list','Adjacency matrix','Two-dimensional full array','Complete edge table for every pair','An adjacency list stores only edges that actually exist.'),
(5,3,1,'Average-case lookup time in a well-designed hash table is usually what?','O(1)','O(log n)','O(n log n)','O(n squared)','With a controlled load factor and good hashing, lookup is constant on average.'),
(5,3,2,'What is the binary search tree property?','Keys in the left subtree are smaller and keys in the right subtree are larger','Every node has exactly two children','The root is always the largest key','Leaves are stored in a queue','The ordering property enables efficient ordered search when the tree is balanced.'),
(5,3,3,'Which operation on a binary heap is O(log n)?','Insert a new value','Read the root value','Check whether the heap is empty','Access the array length','Insertion may bubble the new value upward along the heap height.'),
(5,3,4,'What is path compression used for in disjoint set union?','Flatten parent links to speed up future find operations','Store paths in a graph matrix','Remove all cycles from a graph','Sort set members by name','Path compression makes nodes point closer to the representative after find.'),
(5,3,5,'Which data structure best supports retrieving the current smallest element repeatedly?','Min-heap','Stack','Linked list without sorting','Hash set','A min-heap keeps the minimum at the root and supports efficient extraction.'),
(5,4,1,'Dynamic programming is most suitable when a problem has which characteristics?','Overlapping subproblems and optimal substructure','Only one possible solution','No repeated computation','A strictly random process','DP stores solutions to recurring subproblems and combines optimal smaller results.'),
(5,4,2,'Which technique is commonly used to find the longest substring without repeated characters?','Sliding window with a frequency map','Merge sort','Binary heap construction','Depth-first recursion only','A sliding window expands and contracts while tracking characters in the current range.'),
(5,4,3,'When is a greedy algorithm trustworthy?','When its greedy-choice property and optimal substructure are proven','Whenever it produces a quick answer','Only when input is sorted','When recursion depth is small','A greedy choice needs a correctness argument; speed alone is not enough.'),
(5,4,4,'What is the worst-case time complexity of quicksort with consistently poor pivots?','O(n squared)','O(log n)','O(n)','O(n log log n)','Unbalanced partitions such as always choosing the smallest pivot lead to quadratic work.'),
(5,4,5,'Backtracking differs from ordinary recursion because it does what?','Explores a choice, then undoes it when that branch cannot lead to a solution','Always uses a queue','Never revisits state','Requires a sorted array','Backtracking systematically tries alternatives and restores state after each branch.');

CREATE TEMP TABLE v43_marketplace_chapter_bank (
    product_no INTEGER NOT NULL,
    step_no INTEGER NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    PRIMARY KEY (product_no, step_no)
) ON COMMIT DROP;

INSERT INTO v43_marketplace_chapter_bank VALUES
(3,1,'Nền tảng truy vấn và mô hình quan hệ','Luyện SELECT, JOIN, GROUP BY, NULL và ràng buộc dữ liệu với các tình huống báo cáo thực tế.'),
(3,2,'Index và đọc kế hoạch thực thi','Phân tích B-tree, chỉ mục ghép, EXPLAIN ANALYZE, index-only scan và đánh đổi chi phí ghi.'),
(3,3,'Transaction, lock và độ tin cậy dữ liệu','Áp dụng ACID, isolation level, xử lý lost update, deadlock và vòng đời giao dịch.'),
(3,4,'SQL nâng cao và tối ưu production','Thực hành window function, UPSERT, prepared statement, partition pruning và cascade an toàn.'),
(4,1,'Ngữ pháp cốt lõi cho B2 và TOEIC','Củng cố thì, câu điều kiện, bị động, mệnh đề quan hệ và collocation trong ngữ cảnh công việc.'),
(4,2,'Từ vựng và cấu trúc câu học thuật','Rèn word form, giới từ, liên từ, cấu trúc song song và từ vựng chuyên nghiệp.'),
(4,3,'Đọc hiểu và viết email công việc','Xác định suy luận, ý chính, thông tin chi tiết và viết yêu cầu lịch sự, rõ ràng.'),
(4,4,'Ứng dụng B2 trong bài thi và môi trường làm việc','Luyện modal deduction, reported speech, thông báo TOEIC và biên tập câu văn súc tích.'),
(5,1,'Mảng, độ phức tạp và cấu trúc tuyến tính','Nắm Big-O, binary search, dynamic array, sorting stability và stack qua bài toán nền tảng.'),
(5,2,'Đồ thị và thuật toán duyệt','Phân biệt BFS, Dijkstra, topo sort, visited set và biểu diễn graph thưa.'),
(5,3,'Cấu trúc dữ liệu truy xuất hiệu quả','Vận dụng hash table, BST, heap và disjoint set để tổ chức dữ liệu và truy vấn nhanh.'),
(5,4,'Kỹ thuật giải thuật nâng cao','Chọn đúng dynamic programming, sliding window, greedy, quicksort và backtracking.');

CREATE TEMP TABLE v43_marketplace_content (
    product_no INTEGER PRIMARY KEY,
    content_json JSONB NOT NULL
) ON COMMIT DROP;

INSERT INTO v43_marketplace_content (product_no, content_json)
SELECT c.product_no,
       jsonb_build_object('chapters', jsonb_agg(
           jsonb_build_object(
               'sequenceNo', c.step_no,
               'title', c.title,
               'summary', c.summary,
               'quiz', jsonb_build_object(
                   'title', 'Đánh giá ' || c.title,
                   'questions', (
                       SELECT jsonb_agg(jsonb_build_object(
                           'questionId', v43_seed_uuid('skillsprint-v28', format('question:%s:%s:%s', q.product_no, q.step_no, q.question_no))::text,
                           'type', 'SINGLE_CHOICE',
                           'text', q.question_text,
                           'explanation', q.explanation,
                           'sequenceNo', q.question_no,
                           'options', jsonb_build_array(
                               jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:1', q.product_no, q.step_no, q.question_no))::text, 'label', 'A', 'text', q.option_a, 'correct', TRUE, 'sequenceNo', 1),
                               jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:2', q.product_no, q.step_no, q.question_no))::text, 'label', 'B', 'text', q.option_b, 'correct', FALSE, 'sequenceNo', 2),
                               jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:3', q.product_no, q.step_no, q.question_no))::text, 'label', 'C', 'text', q.option_c, 'correct', FALSE, 'sequenceNo', 3),
                               jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:4', q.product_no, q.step_no, q.question_no))::text, 'label', 'D', 'text', q.option_d, 'correct', FALSE, 'sequenceNo', 4)
                           )
                       ) ORDER BY q.question_no)
                       FROM v43_marketplace_question_bank q
                       WHERE q.product_no = c.product_no AND q.step_no = c.step_no
                   )
               )
           ) ORDER BY c.step_no
       ))
FROM v43_marketplace_chapter_bank c
GROUP BY c.product_no;

CREATE TEMP TABLE v43_marketplace_targets (
    product_no INTEGER NOT NULL,
    item_id UUID NOT NULL PRIMARY KEY,
    description TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO v43_marketplace_targets VALUES
(3, '9056c0f2-c9f5-440e-9f85-d615aee33d2c', 'Bộ luyện 20 câu theo bốn chuyên đề SQL production: truy vấn quan hệ, index và execution plan, transaction an toàn, cùng các kỹ thuật window function và UPSERT. Mỗi câu có bốn lựa chọn, đáp án và giải thích ngắn để ôn phỏng vấn backend hoặc database engineer.'),
(4, 'a49de0b4-2490-4ab7-b8cb-9019d363e9cf', 'Bộ luyện 20 câu B2 Vstep và TOEIC 750+ bao quát ngữ pháp, từ vựng, đọc hiểu và giao tiếp công việc. Nội dung có ngữ cảnh email, thông báo, suy luận từ bài đọc; mỗi câu kèm giải thích để người học tự rà soát lỗi.'),
(5, '8c903e79-cfa5-45cf-96b2-0a42ef8b72b0', 'Bộ luyện 20 câu cấu trúc dữ liệu và giải thuật theo lộ trình từ Big-O, mảng, stack đến graph, heap, dynamic programming và backtracking. Câu hỏi tập trung vào điều kiện áp dụng, độ phức tạp và cách chọn giải pháp trong bài phỏng vấn kỹ thuật.'),
(3, v43_seed_uuid('skillsprint-v28', 'item:3'), 'Bộ luyện 20 câu theo bốn chuyên đề SQL production: truy vấn quan hệ, index và execution plan, transaction an toàn, cùng các kỹ thuật window function và UPSERT. Mỗi câu có bốn lựa chọn, đáp án và giải thích ngắn để ôn phỏng vấn backend hoặc database engineer.'),
(4, v43_seed_uuid('skillsprint-v28', 'item:4'), 'Bộ luyện 20 câu B2 Vstep và TOEIC 750+ bao quát ngữ pháp, từ vựng, đọc hiểu và giao tiếp công việc. Nội dung có ngữ cảnh email, thông báo, suy luận từ bài đọc; mỗi câu kèm giải thích để người học tự rà soát lỗi.'),
(5, v43_seed_uuid('skillsprint-v28', 'item:5'), 'Bộ luyện 20 câu cấu trúc dữ liệu và giải thuật theo lộ trình từ Big-O, mảng, stack đến graph, heap, dynamic programming và backtracking. Câu hỏi tập trung vào điều kiện áp dụng, độ phức tạp và cách chọn giải pháp trong bài phỏng vấn kỹ thuật.');

UPDATE marketplace_items item
SET price_coins = 50000,
    description = target.description,
    updated_at = GREATEST(item.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_targets target
WHERE item.item_id = target.item_id;

UPDATE marketplace_pack_versions version
SET price_coins = 50000,
    description = target.description,
    content_json = COALESCE(content.content_json, version.content_json),
    chapter_count = CASE WHEN content.content_json IS NULL THEN version.chapter_count ELSE 4 END,
    quiz_count = CASE WHEN content.content_json IS NULL THEN version.quiz_count ELSE 4 END,
    question_count = CASE WHEN content.content_json IS NULL THEN version.question_count ELSE 20 END,
    updated_at = GREATEST(version.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_targets target
LEFT JOIN v43_marketplace_content content ON content.product_no = target.product_no
WHERE version.legacy_item_id = target.item_id;

UPDATE marketplace_quiz_pack_snapshots snapshot
SET content_json = content.content_json,
    chapter_count = 4,
    quiz_count = 4,
    question_count = 20,
    updated_at = GREATEST(snapshot.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_targets target
JOIN v43_marketplace_content content ON content.product_no = target.product_no
WHERE snapshot.item_id = target.item_id;

UPDATE roadmap_steps step
SET title = chapter.title,
    subtitle = 'Lý thuyết trọng tâm, tình huống ứng dụng và đánh giá cuối chương',
    summary = chapter.summary,
    what_to_learn = jsonb_build_array(chapter.title, 'Giải thích đáp án và cách tránh lỗi thường gặp'),
    key_concepts = jsonb_build_array('Khái niệm cốt lõi', 'Điều kiện áp dụng', 'Đánh đổi khi triển khai'),
    learning_outcomes = jsonb_build_array('Trả lời đúng câu hỏi nền tảng', 'Giải thích được lựa chọn của mình'),
    recommended_focus = jsonb_build_array('Đọc kỹ giả thiết', 'Đối chiếu đáp án với giải thích'),
    updated_at = GREATEST(step.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_chapter_bank chapter
WHERE step.step_id = v43_seed_uuid('skillsprint-v28', format('step:%s:%s', chapter.product_no, chapter.step_no));

UPDATE quiz_questions question
SET question_text = bank.question_text,
    explanation = bank.explanation,
    updated_at = GREATEST(question.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_question_bank bank
WHERE question.question_id = v43_seed_uuid('skillsprint-v28', format('question:%s:%s:%s', bank.product_no, bank.step_no, bank.question_no));

UPDATE quiz_options option
SET option_text = choices.option_text,
    updated_at = GREATEST(option.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM v43_marketplace_question_bank bank
CROSS JOIN LATERAL unnest(ARRAY[bank.option_a, bank.option_b, bank.option_c, bank.option_d]) WITH ORDINALITY choices(option_text, option_no)
WHERE option.option_id = v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:%s', bank.product_no, bank.step_no, bank.question_no, choices.option_no));

WITH attempt_questions AS (
    SELECT content.product_no,
           jsonb_build_object('questions', jsonb_agg(
               jsonb_build_object(
                   'questionId', v43_seed_uuid('skillsprint-v28', format('question:%s:%s:%s', bank.product_no, bank.step_no, bank.question_no))::text,
                   'type', 'SINGLE_CHOICE',
                   'text', bank.question_text,
                   'options', jsonb_build_array(
                       jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:1', bank.product_no, bank.step_no, bank.question_no))::text, 'label', 'A', 'text', bank.option_a),
                       jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:2', bank.product_no, bank.step_no, bank.question_no))::text, 'label', 'B', 'text', bank.option_b),
                       jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:3', bank.product_no, bank.step_no, bank.question_no))::text, 'label', 'C', 'text', bank.option_c),
                       jsonb_build_object('optionId', v43_seed_uuid('skillsprint-v28', format('option:%s:%s:%s:4', bank.product_no, bank.step_no, bank.question_no))::text, 'label', 'D', 'text', bank.option_d)
                   )
               ) ORDER BY bank.step_no, bank.question_no
           )) AS question_snapshot_json
    FROM v43_marketplace_content content
    JOIN v43_marketplace_question_bank bank ON bank.product_no = content.product_no
    GROUP BY content.product_no
)
UPDATE marketplace_ranked_attempts attempt
SET question_snapshot_json = snapshots.question_snapshot_json,
    updated_at = GREATEST(attempt.updated_at, TIMESTAMPTZ '2026-07-28 11:00:00+07')
FROM marketplace_ranked_quiz_definitions definition
JOIN marketplace_pack_versions version ON version.version_id = definition.pack_version_id
JOIN attempt_questions snapshots ON version.legacy_item_id = v43_seed_uuid('skillsprint-v28', 'item:' || snapshots.product_no)
WHERE attempt.definition_id = definition.definition_id;

DO $$
DECLARE
    v_catalogue_count INTEGER;
    v_detailed_version_count INTEGER;
    v_placeholder_count INTEGER;
BEGIN
    SELECT count(*) INTO v_catalogue_count
    FROM marketplace_items
    WHERE item_id IN (SELECT item_id FROM v43_marketplace_targets)
      AND price_coins = 50000;

    IF v_catalogue_count <> 6 THEN
        RAISE EXCEPTION 'V43 expected six seeded catalogue items at 50,000 Coin, found %', v_catalogue_count;
    END IF;

    SELECT count(*) INTO v_detailed_version_count
    FROM marketplace_pack_versions version
    WHERE version.legacy_item_id IN (
        v43_seed_uuid('skillsprint-v28', 'item:3'),
        v43_seed_uuid('skillsprint-v28', 'item:4'),
        v43_seed_uuid('skillsprint-v28', 'item:5')
    )
      AND version.price_coins = 50000
      AND jsonb_array_length(version.content_json -> 'chapters') = 4;

    IF v_detailed_version_count <> 3 THEN
        RAISE EXCEPTION 'V43 expected three detailed V28 pack versions, found %', v_detailed_version_count;
    END IF;

    SELECT count(*) INTO v_placeholder_count
    FROM quiz_questions question
    WHERE question.question_id IN (
        SELECT v43_seed_uuid('skillsprint-v28', format('question:%s:%s:%s', bank.product_no, bank.step_no, bank.question_no))
        FROM v43_marketplace_question_bank bank
    )
      AND question.question_text LIKE '%câu hỏi chương%';

    IF v_placeholder_count <> 0 THEN
        RAISE EXCEPTION 'V43 still contains % placeholder marketplace questions', v_placeholder_count;
    END IF;
END $$;
