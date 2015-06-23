package models;

import com.avaje.ebean.Ebean;
import minderengine.TestEngine;
import mtdl.SignalSlot;
import play.Logger;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.*;

/**
 * Created by yerlibilgin on 22/12/14.
 */
@Entity
@Table(name = "TestCase")
public class TestCase extends Model {
  @Id
  public long id;

  @ManyToOne
  @Column(nullable = false)
  public TestAssertion testAssertion;

  @Column(unique = true, nullable = false)
  public String name;

  @Column(nullable = false, length = ModelConstants.SHORT_DESC_LENGTH)
  public String shortDescription;

  @Column(length = ModelConstants.DESCRIPTION_LENGTH)
  public String description;

  @Column(nullable = false, length = ModelConstants.MAX_TDL_LENGTH)
  public String tdl;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  public List<WrapperParam> parameters;

  @ManyToOne
  @Column(nullable = false)
  public User owner;

  private static final Finder<Long, TestCase> find = new Finder<>(Long.class,
      TestCase.class);

  public static TestCase findById(Long id) {
    TestCase byId = find.byId(id);
    byId.owner = User.findById(byId.owner.id);
    return byId;
  }

  public void setTdl(String tdl) {
    this.tdl = tdl;
  }

  public static TestCase findByName(String name) {
    return find.where().eq("name", name).findUnique();
  }

  public static TestCase findByTestAssertionId(Long assertionId) {
    return find.where().eq("testAssertion.id", assertionId).findUnique();
  }

  private void detectParameters() {
    deleteCurrentWrapperParamsOnDatabase();
    deleteCurrentJobsOnDatabase();
    LinkedHashMap<String, Set<SignalSlot>> descriptionMap = TestEngine.describeTdl(this);

    List<WrapperParam> wpList = new ArrayList<>();
    for (Map.Entry<String, Set<SignalSlot>> entry : descriptionMap.entrySet()) {
      Logger.debug("Wrapper Name: " + entry.getKey() + ": " + entry.getKey().startsWith("$") + "");

      //make sure that we are looping on variables.
      if (!entry.getKey().startsWith("$"))
        continue;


      Logger.debug("\t" + entry.getKey() + " is a variable");
      WrapperParam wrapperParam;
      wrapperParam = new WrapperParam();
      wrapperParam.name = entry.getKey();
      wrapperParam.signatures = new ArrayList<>();
      wrapperParam.testCase = this;
      wpList.add(wrapperParam);


      for (SignalSlot signalSlot : entry.getValue()) {
        ParamSignature ps = new ParamSignature();
        ps.signature = signalSlot.signature().replaceAll("\\s", "");
        ps.wrapperParam = wrapperParam;
        wrapperParam.signatures.add(ps);
      }

      this.parameters.add(wrapperParam);
    }

    for (WrapperParam str : wpList) {
      str.save();
      System.out.println("TO SAVE FOR " + str.name);
      for (ParamSignature signature : str.signatures) {
        System.out.println("Param Signature " + signature.signature);
        signature.save();
      }
    }
    System.out.println("============");
  }

  private void deleteCurrentJobsOnDatabase() {
    List<Job> all = Job.findByTestCase(this);

    for (Job rc : all) {
      System.out.println("Delete " + rc.name);
      List<MappedWrapper> mwlist = MappedWrapper.findByJob(rc);
      for (MappedWrapper mappedWrapper : mwlist) {
        mappedWrapper.delete();
      }

      List<TestRun> twL = TestRun.findByJob(rc);
      for (TestRun testRun : twL) {
        testRun.job = null;
        testRun.save();
      }

      rc.delete();
    }
  }

  private void deleteCurrentWrapperParamsOnDatabase() {
    List<WrapperParam> all = WrapperParam.findByTestCase(this);

    for (WrapperParam wrapperParam : all) {
      System.out.println("Delete " + wrapperParam.name);
      List<MappedWrapper> mwlist = MappedWrapper.findByWrapperParam(wrapperParam);
      for (MappedWrapper mappedWrapper : mwlist) {
        mappedWrapper.delete();
      }
      wrapperParam.delete();
    }

  }


  @Override
  public void save() {
    try {
      Ebean.beginTransaction();
      super.save();
      detectParameters();
      Ebean.commitTransaction();
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new IllegalArgumentException(ex);
    } finally{
      Ebean.endTransaction();
    }
  }

  @Override
  public void update() {
    try {
      Ebean.beginTransaction();
      super.update();
      detectParameters();
      Ebean.commitTransaction();
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new IllegalArgumentException(ex);
    } finally{
      Ebean.endTransaction();
    }
  }

  public static void updateUser(User user, User localUser) {
  }

  public static List<TestCase> listByTestAssertionId(Long assertionId) {
    return find.where().eq("testAssertion.id", assertionId).findList();
  }
}
