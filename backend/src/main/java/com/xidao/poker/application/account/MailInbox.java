package com.xidao.poker.application.account;

import java.util.List;

public record MailInbox(List<MailItem> messages, long unreadCount) {
    public MailInbox { messages = List.copyOf(messages); }
}
