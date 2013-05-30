package com.atlassian.jira.threadedcomments.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.transaction.TransactionCallback;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Hashtable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A resource of message.
 */
@Path("/")
public class HandleComments {

    private static final Logger log = LogManager.getLogger("votecomments");
    private ActiveObjects ao;
    private IssueManager issueManager;
    private PermissionManager permissionManager;
    private CommentManager commentManager;

    public HandleComments(ActiveObjects ao, IssueManager issueManager, PermissionManager permissionManager, CommentManager commentManager) {
        this.ao = checkNotNull(ao);
        this.issueManager = issueManager;
        this.permissionManager = permissionManager;
        this.commentManager = commentManager;
    }

    @GET
    @AnonymousAllowed
    @Produces({MediaType.APPLICATION_JSON})
    @Path("commentdata")
    public Response commentData(@QueryParam("issueid") final Long issueid)
    {
        if (null == issueid) {
            return Response.notModified("Issue Id missing").build();
        }
        else {
            log.debug("Issueid - " +  issueid);
        }
        final User loggedInUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        final MutableIssue issueObject = issueManager.getIssueObject(issueid);
        final Hashtable<Integer, CommentModel> commentData = new Hashtable<Integer, CommentModel>();
        if (null != issueObject && permissionManager.hasPermission(Permissions.VIEW_VOTERS_AND_WATCHERS, issueObject, loggedInUser)) {
            ao.executeInTransaction(new TransactionCallback<Void>() {
                @Override
                public Void doInTransaction() {
                    CommentInfo[] commentInfos = ao.find(CommentInfo.class, "ISSUE_ID = ?", issueid);
                    for(CommentInfo c : commentInfos) {
                        commentData.put(c.getID(), new CommentModel("",c.getParentCommentId(),c.getIssueId(), c.getCommentId()));
                    }
                    return null;
                }
            });

        }
        else
        {
            log.warn("Get comment request ignored");
        }
        return Response.ok(commentData.values()).build();
    }

    @POST
    @AnonymousAllowed
    @Produces({MediaType.APPLICATION_JSON})
    @Path("addcomment")
    public Response addComment(final CommentModel comment)
    {
        final Comment commentObj = commentManager.getCommentById(comment.getParentCommentId());

        if ( null == comment || (null == comment.getIssueId()) ||
                (null == comment.getParentCommentId()) ||
                (null == comment.getCommentBody())) {
            return Response.notModified("Required parameters missing").build();
        }
        if(null == commentObj){
            return Response.notModified("Wrong comment id").build();
        }

        final Comment newComment = commentManager.create(issueManager.getIssueObject(comment.getIssueId()),
                ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser().getName(),
                StringEscapeUtils.unescapeHtml4(comment.getCommentBody().replaceAll("\\n","\n")), true);
        log.debug(newComment.getId());
        ao.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                final CommentInfo commentInfo = ao.create(CommentInfo.class);
                commentInfo.setCommentId(newComment.getId());
                commentInfo.setParentCommentId(comment.getParentCommentId());
                commentInfo.setIssueId(comment.getIssueId());
                commentInfo.save();
                return null;
            }
        });
        comment.setCommentId(newComment.getId());

        return Response.ok(comment).build();
    }
}