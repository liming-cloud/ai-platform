package com.aiplatform.identity.application.port;

import com.aiplatform.identity.application.command.ReplaceIamMenuFeaturesCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.AssignIamTenantAdministratorCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamRoleDefaultMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.command.CreateIamTenantCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantCommand;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;

/**
 * IAM 配置写入端口，隔离应用层命令编排和 MyBatis 持久化细节。
 */
public interface IamConfigurationCommandPort {
    String createTenant(CreateIamTenantCommand command);

    void updateTenant(UpdateIamTenantCommand command);

    void changeTenantStatus(ChangeIamTenantStatusCommand command);

    void assignTenantAdministrator(AssignIamTenantAdministratorCommand command);

    String createMenu(CreateIamMenuCommand command);

    void updateMenu(UpdateIamMenuCommand command);

    void replaceMenuFeatures(ReplaceIamMenuFeaturesCommand command);

    void updateTenantFeature(UpdateIamTenantFeatureCommand command);

    void replaceRoleMenus(ReplaceIamRoleMenusCommand command);

    void replaceRolePermissions(ReplaceIamRolePermissionsCommand command);

    void updateRoleDefaultMenu(UpdateIamRoleDefaultMenuCommand command);
}
