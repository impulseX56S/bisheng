package com.carrot.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.carrot.model.StringMsg;

@Service
public interface MsgService {

	List<StringMsg> getMsgList(String string);

	void insertMsg(StringMsg stm);
	 
}
