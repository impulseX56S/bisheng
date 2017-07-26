package com.carrot.dao;

import java.util.List;

import com.carrot.model.StringMsg;

public interface StringMsgDao {

	List<StringMsg> getMsgList(String str);

	void insertMsg(StringMsg stm);

}
