package com.dynamo.cr.client;


import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class BaseClient {
    protected Client client;
    protected WebResource resource;

    protected void throwRespositoryException(ClientResponse resp) throws RepositoryException {
        throw new RepositoryException(resp.toString() + "\n" + resp.getEntity(String.class), resp.getClientResponseStatus().getStatusCode());
    }

    protected void throwRespositoryException(ClientHandlerException e) throws RepositoryException {
        throw new RepositoryException(e.getMessage(), -1);
    }

    protected void wrapPut(String path, byte[] data) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).put(ClientResponse.class, data);
            if (resp.getStatus() != 200) {
                throwRespositoryException(resp);
            }
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
        }
    }

    protected <T> T wrapGet(String path, Class<T> klass) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).get(ClientResponse.class);
            if (resp.getStatus() != 200) {
                throwRespositoryException(resp);
            }
            return resp.getEntity(klass);
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
            return null; // Never reached
        }
    }

    protected void wrapPut(String path) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).put(ClientResponse.class);
            if (resp.getStatus() != 200 && resp.getStatus() != 204) {
                throwRespositoryException(resp);
            }
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
        }
    }

    protected <T> T wrapPost(String path, Class<T> klass) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).post(ClientResponse.class);
            if (resp.getStatus() != 200) {
                throwRespositoryException(resp);
            }
            return resp.getEntity(klass);
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
            return null; // Never reached
        }
    }

    protected void wrapPost(String path) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).post(ClientResponse.class);
            if (resp.getStatus() != 200 && resp.getStatus() != 204) {
                throwRespositoryException(resp);
            }
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
        }
    }

    protected void wrapDelete(String path) throws RepositoryException {
        try {
            ClientResponse resp = resource.path(path).delete(ClientResponse.class);
            if (resp.getStatus() != 200 && resp.getStatus() != 204) {
                throwRespositoryException(resp);
            }
        }
        catch (ClientHandlerException e) {
            throwRespositoryException(e);
        }
    }

}
