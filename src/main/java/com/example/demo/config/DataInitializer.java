package com.example.demo.config;

import com.example.demo.entity.Video;
import com.example.demo.repository.VideoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final VideoRepository videoRepository;

    public DataInitializer(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (videoRepository.count() > 0) return;

        String[][] data = {
            // title, channel, category, duration, dateText, description, thumbnail, embedUrl
            {
                "자바 스프링 부트 입문 강의 - 처음 시작하는 백엔드 개발",
                "코딩 마스터", "교육",  "32:14", "2주 전",
                "스프링 부트를 처음 접하는 분들을 위한 완벽 입문 강의입니다. JPA, REST API, H2 데이터베이스까지 한 번에!",
                "https://picsum.photos/seed/spring1/480/270", ""
            },
            {
                "2026년 서울 벚꽃 명소 BEST 5 브이로그",
                "여행하는 민지", "여행", "18:42", "3일 전",
                "올해 벚꽃 시즌 서울에서 가장 예쁜 스팟 5곳을 직접 다녀왔어요. 여의도, 석촌호수, 남산, 창경궁, 하늘공원!",
                "https://picsum.photos/seed/cherry1/480/270", ""
            },
            {
                "매운 불닭볶음면 10봉지 먹방 도전 (경고: 실제로 매움)",
                "먹방왕 철수", "엔터테인먼트", "24:07", "5일 전",
                "구독자 10만 기념으로 불닭볶음면 10봉지에 도전했습니다. 과연 성공할 수 있을까요?",
                "https://picsum.photos/seed/food1/480/270", ""
            },
            {
                "로스트아크 신규 레이드 공략 - 베히모스 패턴 완벽 분석",
                "게임채널 알파", "게임", "41:55", "1주 전",
                "베히모스 레이드 전 패턴 정리 및 공략법을 상세하게 설명합니다. 클리어율 100% 보장!",
                "https://picsum.photos/seed/game1/480/270", ""
            },
            {
                "하루 30분 홈트 루틴 - 덤벨 없이 전신 운동 (4주 챌린지)",
                "헬스 코치 유나", "스포츠", "29:38", "2주 전",
                "집에서 맨몸으로 할 수 있는 효과적인 전신 운동 루틴입니다. 4주 꾸준히 하면 몸이 달라집니다.",
                "https://picsum.photos/seed/fitness1/480/270", ""
            },
            {
                "GPT-5 출시 - AI가 바꾼 개발자의 하루 (현직 개발자 솔직 후기)",
                "테크 인사이더", "과학기술", "15:21", "어제",
                "GPT-5를 실제 업무에 적용해본 현직 개발자의 솔직한 사용기입니다. 생산성이 얼마나 올랐을까요?",
                "https://picsum.photos/seed/tech1/480/270", ""
            },
            {
                "한강 공원 자전거 라이딩 VLOG - 서울에서 힐링하기",
                "라이더 준호", "여행", "12:48", "4일 전",
                "주말에 한강 자전거 길을 따라 라이딩한 브이로그입니다. 날씨도 좋고 경치도 최고였어요!",
                "https://picsum.photos/seed/bike1/480/270", ""
            },
            {
                "파이썬으로 유튜브 자동화 봇 만들기 (실전 프로젝트)",
                "코딩 마스터", "교육", "55:03", "3주 전",
                "Python과 YouTube Data API를 활용해서 영상 자동 업로드 봇을 만드는 실전 프로젝트입니다.",
                "https://picsum.photos/seed/python1/480/270", ""
            },
            {
                "신혼집 인테리어 공개 - 30평 아파트 셀프 인테리어 총정리",
                "우리집 스튜디오", "라이프스타일", "38:19", "1주 전",
                "신혼집 인테리어를 셀프로 진행한 전 과정을 공개합니다. 예산부터 시공까지 모두 담았어요.",
                "https://picsum.photos/seed/interior1/480/270", ""
            },
            {
                "K-POP 댄스 커버 - NewJeans 'How Sweet' (연습영상 포함)",
                "댄스 스튜디오 K", "음악", "08:55", "6일 전",
                "NewJeans의 최신곡 'How Sweet' 안무 커버 영상입니다. 연습 영상도 함께 담았어요.",
                "https://picsum.photos/seed/dance1/480/270", ""
            },
            {
                "제주도 3박 4일 여행 완벽 가이드 - 숨은 맛집과 명소 공개",
                "여행하는 민지", "여행", "27:44", "2주 전",
                "제주도 여행 계획 중이라면 꼭 보세요! 현지인 추천 맛집과 잘 알려지지 않은 명소를 소개합니다.",
                "https://picsum.photos/seed/jeju1/480/270", ""
            },
            {
                "주식 초보 탈출 - 배당주 포트폴리오 구성하는 법 (실전 공개)",
                "재테크 연구소", "금융", "22:11", "5일 전",
                "월 배당금 50만원을 목표로 포트폴리오를 구성하는 방법을 실제 계좌와 함께 공개합니다.",
                "https://picsum.photos/seed/stock1/480/270", ""
            }
        };

        for (String[] d : data) {
            Video v = new Video();
            v.setTitle(d[0]);
            v.setChannel(d[1]);
            v.setCategory(d[2]);
            v.setDuration(d[3]);
            v.setDateText(d[4]);
            v.setDescription(d[5]);
            v.setThumbnail(d[6]);
            v.setEmbedUrl(d[7]);
            v.setVisibility("공개");
            v.setAvatar("https://picsum.photos/seed/" + d[1].hashCode() + "/40/40");
            v.setOwnerId(null);
            v.setVideoUrl("");
            videoRepository.save(v);
        }
    }
}
