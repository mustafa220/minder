package editormodels;

import models.ModelConstants;
import play.data.validation.Constraints;

/**
* Created by yerlibilgin on 03/05/15.
*/
public class UserEditorModel {
  public Long id;

  @Constraints.Email
  @Constraints.Required
  public String email;

  @Constraints.Required
  public String name;

  @Constraints.MinLength(5)
  public String password;


  @Constraints.MinLength(5)
  public String repeatPassword;

  public String istd;

  public String ists;

  public String isto;
}
