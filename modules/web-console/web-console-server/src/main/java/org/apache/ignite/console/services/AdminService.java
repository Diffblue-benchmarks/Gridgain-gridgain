/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.console.services;

import java.util.List;
import java.util.UUID;
import org.apache.ignite.console.dto.Account;
import org.apache.ignite.console.dto.Announcement;
import org.apache.ignite.console.json.JsonArray;
import org.apache.ignite.console.json.JsonObject;
import org.apache.ignite.console.repositories.AnnouncementRepository;
import org.apache.ignite.console.tx.TransactionManager;
import org.apache.ignite.console.web.model.SignUpRequest;
import org.apache.ignite.console.web.socket.WebSocketHandler;
import org.apache.ignite.transactions.Transaction;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import static org.apache.ignite.console.notification.NotificationDescriptor.ACCOUNT_DELETED;
import static org.apache.ignite.console.notification.NotificationDescriptor.ADMIN_WELCOME_LETTER;

/**
 * Service to handle administrator actions.
 */
@Service
public class AdminService {
    /** */
    private final TransactionManager txMgr;

    /** */
    private final AccountsService accountsSrv;

    /** */
    private final ConfigurationsService cfgsSrv;

    /** */
    private final NotebooksService notebooksSrv;

    /** */
    private final NotificationService notificationSrv;

    /** */
    private final AnnouncementRepository annRepo;

    /** */
    private final WebSocketHandler wsm;

    /**
     * @param txMgr Transactions manager.
     * @param accountsSrv Service to work with accounts.
     * @param cfgsSrv Service to work with configurations.
     * @param notebooksSrv Service to work with notebooks.
     * @param notificationSrv Service to send notifications.
     * @param annRepo Repository to work with announcement.
     * @param wsm Web sockets manager.
     */
    public AdminService(
        TransactionManager txMgr,
        AccountsService accountsSrv,
        ConfigurationsService cfgsSrv,
        NotebooksService notebooksSrv,
        NotificationService notificationSrv,
        AnnouncementRepository annRepo,
        WebSocketHandler wsm
    ) {
        this.txMgr = txMgr;
        this.accountsSrv = accountsSrv;
        this.cfgsSrv = cfgsSrv;
        this.notebooksSrv = notebooksSrv;
        this.notificationSrv = notificationSrv;
        this.annRepo = annRepo;
        this.wsm = wsm;
    }

    /**
     * @return List of all users.
     */
    public JsonArray list() {
        List<Account> accounts = accountsSrv.list();

        JsonArray res = new JsonArray();

        accounts.forEach(account ->
            res.add(new JsonObject()
                .add("id", account.getId())
                .add("firstName", account.getFirstName())
                .add("lastName", account.getLastName())
                .add("admin", account.getAdmin())
                .add("email", account.getUsername())
                .add("company", account.getCompany())
                .add("country", account.getCountry())
                .add("lastLogin", account.lastLogin())
                .add("lastActivity", account.lastActivity())
                .add("activated", account.isEnabled())
                .add("counters", new JsonObject()
                    .add("clusters", 0)
                    .add("caches", 0)
                    .add("models", 0)
                    .add("igfs", 0))
            )
        );

        return res;
    }

    /**
     * Delete account by ID.
     *
     * @param accId Account ID.
     */
    public void delete(UUID accId) {
        try (Transaction tx = txMgr.txStart()) {
            cfgsSrv.deleteByAccountId(accId);
            notebooksSrv.deleteByAccountId(accId);
            Account acc = accountsSrv.delete(accId);

            tx.commit();

            notificationSrv.sendEmail(ACCOUNT_DELETED, acc);
        }
    }

    /**
     * @param accId Account ID.
     * @param admin Admin flag.
     */
    public void toggle(UUID accId, boolean admin) {
        accountsSrv.toggle(accId, admin);
    }

    /**
     * @param accId Account ID.
     */
    public void become(UUID accId) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    /**
     * @param params SignUp params.
     */
    public void registerUser(SignUpRequest params) {
        Account acc = accountsSrv.create(params);

        notificationSrv.sendEmail(ADMIN_WELCOME_LETTER, acc);
    }

    /** */
    @EventListener(ApplicationReadyEvent.class)
    public void initAnnouncement() {
        updateAnnouncement(annRepo.load());
    }

    /**
     * @param ann Announcement.
     */
    public void updateAnnouncement(Announcement ann) {
        annRepo.save(ann);

        wsm.broadcastAnnouncement(ann);
    }
}
