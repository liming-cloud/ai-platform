/** 界面草稿按租户、操作者和登录代次隔离；只保存白名单非秘密字段。 */
export function memberDrafts(storage, session) {
  const prefix='wp0016-member-ui:';
  const owner=session.authenticated ? `${session.tenantId}:${session.accountId}:${session.epoch}:` : null;
  const key=scope=>prefix+owner+scope;
  const clean=()=>{
    for (let index=storage.length-1; index>=0; index--) {
      const name=storage.key(index);
      if ((name.startsWith(prefix)&&(!owner||!name.startsWith(prefix+owner))) || name.startsWith('wp0016-member-form:')) storage.removeItem(name);
    }
  };
  clean();
  return {
    read(scope) { return owner ? JSON.parse(storage.getItem(key(scope)) || 'null') : null; },
    save(scope, input) {
      if (!owner) throw Error('身份已失效');
      const safe=scope==='create' ? {step:input.step,loginName:input.loginName,features:input.features,assistants:input.assistants} : {features:input.features,assistants:input.assistants};
      storage.setItem(key(scope),JSON.stringify(safe));
    },
    clear(scope) { if(owner)storage.removeItem(key(scope)); },
  };
}
