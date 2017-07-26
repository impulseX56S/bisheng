package com.carrot.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.carrot.model.StringMsg;
import com.carrot.service.MsgService;

@Controller
@RequestMapping("/msg")
public class MsgControl {
	private static final String STR_FORMAT__BASE = "WCAR000000";
	@Autowired
	public MsgService msgService;

	@RequestMapping(value = "searchMsg")
	@ResponseBody
	public List<StringMsg> getInfoByFlag(HttpEntity<StringMsg> stringms) {

		List<StringMsg> stringMsgs = msgService.getMsgList(stringms.getBody().getMsg());
		return stringMsgs;
	}

	@RequestMapping(value = "saveMsg")
	@ResponseBody
	public String saveMsg(HttpEntity<StringMsg> stringms) {
		msgService.insertMsg(stringms.getBody());
		String s = stringms.getBody().getId() + "";
		return STR_FORMAT__BASE.substring(0, 10 - s.length()) + s;
	}

}
