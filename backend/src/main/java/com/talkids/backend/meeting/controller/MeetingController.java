package com.talkids.backend.meeting.controller;

import com.talkids.backend.common.annotation.LoginUser;
import com.talkids.backend.common.utils.ApiUtils;
import com.talkids.backend.common.utils.ApiUtils.ApiResult;
import com.talkids.backend.group.entity.Group;
import com.talkids.backend.group.service.GroupService;
import com.talkids.backend.meeting.dto.*;
import com.talkids.backend.meeting.entity.Meeting;
import com.talkids.backend.meeting.entity.MeetingJoinReq;
import com.talkids.backend.meeting.entity.MeetingSchedule;
import com.talkids.backend.meeting.service.MeetingService;
import com.talkids.backend.member.entity.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/meeting")
public class MeetingController {

    private final GroupService groupService;
    private final MeetingService meetingService;


    //등록한 미팅 일정 및 성사된 미팅일정을 년, 월별로 가져온다
    @GetMapping
    public ApiResult getMeetings(@LoginUser Member member,
                                  @RequestParam("year") Integer year,
                                  @RequestParam("month") Integer month) throws Exception{
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(year == null) return ApiUtils.error("year는 필수 정보 입니다", HttpStatus.BAD_REQUEST);
        if(month == null) return ApiUtils.error("month는 필수 정보 입니다", HttpStatus.BAD_REQUEST);

        //해당 년, 월에 해당하는 일정들을 가져오고
        List<Meeting> meetings = meetingService.getMeetingsByYearAndMonth(year, month);
        List<MeetingSchedule> meetingSchedules = meetingService.getMeetingSchedulesByYearAndMonth(year, month);
        
        //내가 속한 그룹을 가져오고
        List<Group> groups = groupService.getGroupList(member);
        Set<Integer> myGroups = new HashSet<>();
        for(Group group: groups) myGroups.add(group.getGroupId());

        List<MeetingWithMineDto> meetingDtos = new ArrayList<>(meetings.size());
        List<MeetingScheduleWithMineDto> meetingScheduleDtos = new ArrayList<>(meetingSchedules.size());

        boolean isMine = false;
        for(Meeting meeting: meetings){
            isMine = myGroups.contains(meeting.getGroupReq().getGroupId()) || myGroups.contains(meeting.getGroupRes().getGroupId());
            if(isMine){
                //성사된 일정에 대해서는 내꺼만 보여주자
                meetingDtos.add(MeetingWithMineDto.fromEntity(meeting, isMine));
            }
        }

        for(MeetingSchedule meetingSchedule: meetingSchedules){
            isMine = myGroups.contains(meetingSchedule.getGroup().getGroupId());
            meetingScheduleDtos.add(MeetingScheduleWithMineDto.fromEntity(meetingSchedule, isMine));
        }

        GetMyMeetingDto.Response response = GetMyMeetingDto.Response.builder()
            .meetings(meetingDtos)
            .meetingSchedules(meetingScheduleDtos)
            .build();

        return ApiUtils.success(response);
    }

    //미팅(빈 일정)을 등록
    @PostMapping("")
    public ApiResult postEmptyMeeting(@LoginUser Member member,
                                        @RequestBody @Valid  PostMeetingDto.Request body) throws Exception{
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);

        try{
            meetingService.postEmptyMeeting(member, body);
            return ApiUtils.success("성공");
        }
        catch(Exception e){
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    //상대방의 미팅(빈 일정)에 신청
    @PostMapping("/apply")
    public ApiResult applyMeetingSchedule(@LoginUser Member member,
                                          @Valid @RequestBody ApplyMeetingScheduleDto.Request body){
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(member.getMemberType().getMemberTypeId() != 1) return ApiUtils.error("선생님만 미팅에 신청할 수 있습니다", HttpStatus.BAD_REQUEST);

        try{
            meetingService.applyMeetingSchedule(member, body.getMeetingScheduleId(), body.getGroupId());
            return ApiUtils.success("성공");  //성공적으로 신청
        }
        catch(Exception e){
            //중간에 에러가 난 경우
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    //미팅 신청 요청 받은 것 가져오기
    @GetMapping("/receive")
    public ApiResult getMyReceiveRequest(@LoginUser Member member){
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(member.getMemberType().getMemberTypeId() != 1) return ApiUtils.error("선생님만 요청을 처리할 수 있습니다", HttpStatus.BAD_REQUEST);

        List<MeetingJoinReq> reqs = meetingService.getReceivedRequest(member);

        return ApiUtils.success(MyReceiveDto.Response.fromEntity(reqs));
    }

    //미팅 신청 한 것 가져오기
    @GetMapping("/send")
    public ApiResult getMySendRequest(@LoginUser Member member){
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(member.getMemberType().getMemberTypeId() != 1) return ApiUtils.error("선생님만 요청을 처리할 수 있습니다", HttpStatus.BAD_REQUEST);

        List<MeetingJoinReq> sends = meetingService.getSendRequest(member);

        return ApiUtils.success(MyReceiveDto.Response.fromEntity(sends));
    }

    //미팅 신청 수락하기
    @PostMapping("/accept")
    public ApiResult acceptRequest(@LoginUser Member member,
                                   @Valid @RequestBody AcceptMeetingDto.Request body){
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(member.getMemberType().getMemberTypeId() != 1) return ApiUtils.error("선생님만 요청을 처리할 수 있습니다", HttpStatus.BAD_REQUEST);

        try{
            meetingService.acceptRequest(member, body.getMeetingJoinReqId());
            return ApiUtils.success("성공");
        }
        catch(Exception e){
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    //미팅 신청 거부하기
    @PostMapping("/reject")
    public ApiResult rejectRequest(@LoginUser Member member,
                                   @Valid @RequestBody AcceptMeetingDto.Request body){
        if(member == null) return ApiUtils.error("로그인 정보가 올바르지 않습니다", HttpStatus.UNAUTHORIZED);
        if(member.getMemberType().getMemberTypeId() != 1) return ApiUtils.error("선생님만 요청을 처리할 수 있습니다", HttpStatus.BAD_REQUEST);

        try{
            meetingService.rejectRequest(member, body.getMeetingJoinReqId());
            return ApiUtils.success("성공");
        }
        catch(Exception e){
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
