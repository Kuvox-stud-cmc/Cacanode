#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "${ROOT_DIR}/en-US" "${ROOT_DIR}/vi-VN"

english=(
  "I built a service that reduced processing time."
  "I clarified the requirements before starting the work."
  "The main challenge was coordinating several dependencies."
  "I tested the change and monitored the release."
  "My role was to design the interface and review the implementation."
  "I would first reproduce the issue in a safe environment."
  "We measured success using latency and error rates."
  "I learned to communicate risks early."
  "The project required careful planning and collaboration."
  "I improved reliability by adding retries and clear timeouts."
  "I documented the decision so the team could maintain it."
  "The result was a simpler workflow for users."
  "I compared two options and selected the safer design."
  "I asked for feedback after the first prototype."
  "The deadline changed, so I reduced the scope responsibly."
  "I handled the incident by restoring service first."
  "I used small releases to reduce operational risk."
  "I explained the tradeoffs to both technical and business teams."
  "I verified the data before presenting the conclusion."
  "The experience taught me to make assumptions explicit."
)

vietnamese=(
  "Tôi đã xây dựng một dịch vụ giúp giảm thời gian xử lý."
  "Tôi làm rõ yêu cầu trước khi bắt đầu công việc."
  "Thách thức chính là phối hợp nhiều phần phụ thuộc."
  "Tôi kiểm thử thay đổi và theo dõi sau khi phát hành."
  "Vai trò của tôi là thiết kế giao diện và rà soát triển khai."
  "Trước tiên tôi sẽ tái hiện lỗi trong môi trường an toàn."
  "Chúng tôi đo kết quả bằng độ trễ và tỷ lệ lỗi."
  "Tôi học được cách thông báo rủi ro sớm."
  "Dự án cần lập kế hoạch cẩn thận và phối hợp tốt."
  "Tôi tăng độ tin cậy bằng cơ chế thử lại và giới hạn thời gian."
  "Tôi ghi lại quyết định để nhóm có thể bảo trì hệ thống."
  "Kết quả là quy trình đơn giản hơn cho người dùng."
  "Tôi so sánh hai phương án và chọn thiết kế an toàn hơn."
  "Tôi xin phản hồi sau bản thử nghiệm đầu tiên."
  "Khi thời hạn thay đổi, tôi giảm phạm vi một cách hợp lý."
  "Tôi xử lý sự cố bằng cách khôi phục dịch vụ trước."
  "Tôi chia nhỏ bản phát hành để giảm rủi ro vận hành."
  "Tôi giải thích đánh đổi cho cả nhóm kỹ thuật và kinh doanh."
  "Tôi xác minh dữ liệu trước khi trình bày kết luận."
  "Kinh nghiệm đó giúp tôi luôn nêu rõ các giả định."
)

generate() {
  local voice="$1" language="$2" index="$3" phrase="$4"
  local aiff="${TMPDIR:-/tmp}/cacanode-interview-${language}-${index}.aiff"
  local output="${ROOT_DIR}/${language}/$(printf '%02d' "${index}").ulaw"
  say -v "${voice}" -o "${aiff}" "${phrase}"
  ffmpeg -hide_banner -loglevel error -y -i "${aiff}" -ar 8000 -ac 1 \
    -c:a pcm_mulaw -f mulaw "${output}"
}

for index in "${!english[@]}"; do
  generate Samantha en-US "$((index + 1))" "${english[index]}"
done
for index in "${!vietnamese[@]}"; do
  generate Linh vi-VN "$((index + 1))" "${vietnamese[index]}"
done
