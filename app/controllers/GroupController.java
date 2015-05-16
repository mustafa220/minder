package controllers;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.ebean.Update;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import editormodels.AssertionEditorModel;
import editormodels.GroupEditorModel;
import global.Util;
import models.TestAssertion;
import models.TestGroup;
import models.User;
import play.Logger;
import play.data.Form;
import play.db.ebean.Model;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.groupDetailView;
import views.html.restrictedTestDesigner;
import views.html.testGroupEditor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static play.data.Form.form;

/**
 * Created by yerlibilgin on 03/05/15.
 */
public class GroupController extends Controller {
  public static final Form<GroupEditorModel> TEST_GROUP_FORM = form(GroupEditorModel.class);


  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result getCreateGroupEditorView() {
    return ok(testGroupEditor.render(TEST_GROUP_FORM, null));
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result doCreateTestGroup() {
    com.feth.play.module.pa.controllers.Authenticate.noCache(response());
    final Form<GroupEditorModel> filledForm = TEST_GROUP_FORM
        .bindFromRequest();
    final User localUser = Application.getLocalUser(session());
    if (filledForm.hasErrors()) {
      Util.printFormErrors(filledForm);
      return badRequest(testGroupEditor.render(filledForm, null));
    } else {
      GroupEditorModel model = filledForm.get();

      TestGroup group = TestGroup.findByName(model.name);
      if (group != null) {
        filledForm.reject("The group with name [" + group.name
            + "] already exists");
        return badRequest(testGroupEditor.render(filledForm, null));
      }

      group = new TestGroup();

      group.owner = localUser;
      group.shortDescription = model.shortDescription;
      group.description = model.description;
      group.name = model.name;

      group.save();
      return redirect(routes.Application.restrictedTestDesigner("testGroups"));
    }
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result editGroupForm(Long id) {
    final User localUser = Application.getLocalUser(session());
    TestGroup tg = TestGroup.find.byId(id);
    if (tg == null) {
      return badRequest("Test group with id [" + id + "] not found!");
    } else {
      GroupEditorModel tgem = new GroupEditorModel();
      tgem.id = tg.id;
      tgem.name = tg.name;
      tgem.shortDescription = tg.shortDescription;
      tgem.description = tg.description;
      Form<GroupEditorModel> bind = TEST_GROUP_FORM.fill(tgem);

      return ok(testGroupEditor.render(bind, null));
    }
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result doEditGroupField() {
    com.feth.play.module.pa.controllers.Authenticate.noCache(response());
    JsonNode jsonNode = request().body().asJson();

    return doEditField(GroupEditorModel.class, TestGroup.class, jsonNode);
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result doEditField(Class<?> editorClass, Class<?> cls, JsonNode jsonNode) {
    long id = jsonNode.findPath("id").asInt();
    String field = jsonNode.findPath("field").asText();
    String newValue = jsonNode.findPath("newValue").asText();
    String converter = jsonNode.findPath("converter").asText();

    System.out.println("Converter3 [" + (converter == null) + "]");
    try {
      try {
        Util.checkField(editorClass, field, newValue);
      } catch (IllegalArgumentException ex) {
        ex.printStackTrace();
        return badRequest(ex.getMessage());
      }

      //check access.
      // first lets see if we have a field named owner
      try {
        Field fld = cls.getDeclaredField("owner");

        //we have an owner. Lets verify if we are the rightfull owner

        String queryString = "find " + cls.getSimpleName() + " where id = :id";
        Query<?> query = Ebean.createQuery(cls, queryString);
        query.setParameter("id", id);
        Object o = query.findUnique();
        User user = (User) fld.get(o);
        System.out.println("UNique " + user.email);

        User localUser = Application.getLocalUser(session());

        if (localUser==null || !localUser.email.equals(user.email)){
          return badRequest("You don't have the permission to modify this resource");
        }
      } catch (NoSuchFieldException ex) {

      }
      String updStatement = "update " + cls.getSimpleName() + " set " + field + " = :value where id = :id";
      Update<?> update = Ebean.createUpdate(cls, updStatement);

      Object newValueConverted = newValue;
      if (converter != null && converter.length() != 0)
        newValueConverted = Util.convertValue(converter, newValue);

      update.set("value", newValueConverted);
      update.set("id", id);
      update.execute();
      ObjectNode node = Json.newObject();
      node.put("value", newValueConverted.toString());
      return ok(node.toString());
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      e.printStackTrace();
      return badRequest(sw.toString());
    }
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result doEditGroup() {
    com.feth.play.module.pa.controllers.Authenticate.noCache(response());

    final Form<GroupEditorModel> filledForm = TEST_GROUP_FORM
        .bindFromRequest();

    if (filledForm.hasErrors()) {
      Util.printFormErrors(filledForm);
      return badRequest(testGroupEditor.render(filledForm, null));
    } else {
      GroupEditorModel model = filledForm.get();
      TestGroup tg = TestGroup.find.byId(model.id);
      tg.name = model.name;
      tg.shortDescription = model.shortDescription;
      tg.description = model.description;
      tg.update();

      Logger.info("Done updating group " + tg.name + ":" + tg.id);
      return ok();
    }
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result doDeleteGroup(Long id) {
    com.feth.play.module.pa.controllers.Authenticate.noCache(response());
    TestGroup tg = TestGroup.find.byId(id);
    if (tg == null) {
      return badRequest("Test group with id [" + id + "] not found!");
    } else {
      try {
        tg.delete();
      } catch (Exception ex) {
        ex.printStackTrace();
        return badRequest(ex.getMessage());
      }
      return ok();
    }
  }

  @Restrict(@Group(Application.TEST_DESIGNER_ROLE))
  public static Result getGroupDetailView(Long id) {
    TestGroup tg = TestGroup.findById(id);
    if (tg == null) {
      return badRequest("No job with id " + id + ".");
    }
    return ok(groupDetailView.render(tg, Application.getLocalUser(session())));
  }


}
