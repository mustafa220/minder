package models;

import com.avaje.ebean.ExpressionList;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.util.List;

/**
 * Created by yerlibilgin on 30/12/14.
 */
@Entity
@DiscriminatorValue("0")
public class Job extends AbstractJob {

  private static final Finder<Long, Job> find = new Finder<>(
      Job.class);

  @ManyToOne
  public TestSuite testSuite;

  public static List<Job> findByTdl(Tdl tdl) {
    ExpressionList<Job> f = find.where().eq("tdl", tdl);
    return f.orderBy().desc("id").findList();
  }


  public static List<Job> getAllByTestSuite(TestSuite testSuite) {
    return find.where().eq("testSuite", testSuite).orderBy("id").findList();
  }

  public static Job findById(Long id) {
    Job byId = find.byId(id);
    if (byId == null)
      return null;
    byId.owner = User.findById(byId.owner.id);
    return byId;
  }

  public static Job findByTdlAndName(Tdl tdl, String name) {
    return find.where().eq("tdl", tdl).eq("name", name).findUnique();
  }

  public static List<Job> findByTestSuite(TestSuite testSuite) {
    return find.where().eq("testSuite", testSuite).setOrderBy("name").findList();
  }

  public static Job findByName(String name) {
    return find.where().eq("name", name).findUnique();
  }

  public static List<Job> findAll() {
    return find.where().setOrderBy("id").findList();
  }
}

