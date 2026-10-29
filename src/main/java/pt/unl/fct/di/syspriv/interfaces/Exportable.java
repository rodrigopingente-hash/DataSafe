package pt.unl.fct.di.syspriv.interfaces;

import pt.unl.fct.di.syspriv.entities.ExportEntity;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

public interface Exportable {

    ExportEntity export(XACMLPDP pdp);

}