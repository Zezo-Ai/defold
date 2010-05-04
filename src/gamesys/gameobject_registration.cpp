#include "gameobject_registration.h"

#include <dlib/log.h>
#include <dlib/dstrings.h>
#include <dlib/event.h>
#include <dlib/hash.h>
#include <ddf/ddf.h>
#include "gameobject.h"
#include <physics/physics.h>
#include "resource_creation.h"

#include "../proto/physics_ddf.h"

namespace dmGameSystem
{
    dmGameObject::CreateResult CreateRigidBody(dmGameObject::HCollection collection,
                                               dmGameObject::HInstance instance,
                                               void* resource,
                                               void* context,
                                               uintptr_t* user_data)
    {
        assert(user_data);

        RigidBodyPrototype* rigid_body_prototype = (RigidBodyPrototype*) resource;
        dmPhysics::HWorld world = (dmPhysics::HWorld) context;

        Point3 position = dmGameObject::GetPosition(instance);
        Quat rotation = Quat::identity();
        rotation = Quat::rotationZ(0.4f); // TODO: <--- HAXXOR JUST FOR FUN...

        dmPhysics::HRigidBody rigid_body = dmPhysics::NewRigidBody(world, rigid_body_prototype->m_CollisionShape, instance, rotation, position, rigid_body_prototype->m_Mass);
        *user_data = (uintptr_t) rigid_body;
        return dmGameObject::CREATE_RESULT_OK;
    }

    dmGameObject::CreateResult DestroyRigidBody(dmGameObject::HCollection collection,
                                                dmGameObject::HInstance instance,
                                                void* context,
                                                uintptr_t* user_data)
    {
        assert(user_data);
        dmPhysics::HWorld world = (dmPhysics::HWorld) context;

        dmPhysics::HRigidBody rigid_body = (dmPhysics::HRigidBody) *user_data;
        dmPhysics::DeleteRigidBody(world, rigid_body);
        return dmGameObject::CREATE_RESULT_OK;
    }

    void UpdateRigidBody(dmGameObject::HCollection collection,
                         const dmGameObject::UpdateContext* update_context,
                         void* context)
    {
        dmPhysics::HWorld world = (dmPhysics::HWorld) context;
        dmPhysics::StepWorld(world, update_context->m_DT);
    }

    void OnEventRigidBody(dmGameObject::HCollection collection,
    		dmGameObject::HInstance instance,
			const dmGameObject::ScriptEventData* event_data,
			void* context,
			uintptr_t* user_data)
    {
        if (event_data->m_EventHash == dmHashString32("ApplyForce"))
        {
        	dmPhysics::ApplyForceMessage* af = (dmPhysics::ApplyForceMessage*) event_data->m_DDFData;
            dmPhysics::HRigidBody rigid_body = (dmPhysics::HRigidBody) *user_data;
            Vector3 force(af->m_Force.m_X, af->m_Force.m_Y, af->m_Force.m_Z);
            Vector3 rel_pos(af->m_RelativePosition.m_X, af->m_RelativePosition.m_Y, af->m_RelativePosition.m_Z);
            dmPhysics::ApplyForce(rigid_body, force, rel_pos);
        }
    }

    dmGameObject::Result RegisterPhysicsComponent(dmResource::HFactory factory,
                                                  dmGameObject::HCollection collection,
                                                  dmPhysics::HWorld physics_world)
    {
        uint32_t type;

    	dmGameObject::RegisterDDFType(dmPhysics::ApplyForceMessage::m_DDFDescriptor);
    	dmEvent::Register(dmHashString32(dmPhysics::ApplyForceMessage::m_DDFDescriptor->m_Name), sizeof(dmGameObject::ScriptEventData) + sizeof(dmPhysics::ApplyForceMessage));

    	dmResource::FactoryResult fact_result = dmResource::GetTypeFromExtension(factory, "rigidbody", &type);
        if (fact_result != dmResource::FACTORY_RESULT_OK)
        {
            dmLogWarning("Unable to get resource type for 'rigidbody' (%d)", fact_result);
            return dmGameObject::RESULT_UNKNOWN_ERROR;
        }
        dmGameObject::Result res = dmGameObject::RegisterComponentType(collection, "rigidbody", type, physics_world, &CreateRigidBody, &DestroyRigidBody, &UpdateRigidBody, &OnEventRigidBody, true);
        return res;
    }
}
