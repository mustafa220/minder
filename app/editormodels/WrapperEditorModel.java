package editormodels;

import models.ModelConstants;
import play.data.validation.Constraints;

/**
* Created by yerlibilgin on 03/05/15.
*/
public class WrapperEditorModel {
  public Long id;

  @Constraints.Required
  public String name;

  @Constraints.Required
  @Constraints.MinLength(ModelConstants.MIN_DESC_LENGTH)
  @Constraints.MaxLength(ModelConstants.SHORT_DESC_LENGTH)
  public String shortDescription;

}
