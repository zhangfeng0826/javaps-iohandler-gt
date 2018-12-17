/**
 * Copyright (C) 2007 - 2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * As an exception to the terms of the GPL, you may copy, modify,
 * propagate, and distribute a work formed by combining 52°North WPS
 * GeoTools Modules with the Eclipse Libraries, or a work derivative of
 * such a combination, even if such copying, modification, propagation, or
 * distribution would otherwise violate the terms of the GPL. Nothing in
 * this exception exempts you from complying with the GPL in all respects
 * for all of the code used other than the Eclipse Libraries. You may
 * include this exception and its grant of permissions when you distribute
 * 52°North WPS GeoTools Modules. Inclusion of this notice with such a
 * distribution constitutes a grant of such permissions. If you do not wish
 * to grant these permissions, remove this paragraph from your
 * distribution. "52°North WPS GeoTools Modules" means the 52°North WPS
 * modules using GeoTools functionality - software licensed under version 2
 * or any later version of the GPL, or a work based on such software and
 * licensed under the GPL. "Eclipse Libraries" means Eclipse Modeling
 * Framework Project and XML Schema Definition software distributed by the
 * Eclipse Foundation and licensed under the Eclipse Public License Version
 * 1.0 ("EPL"), or a work based on such software and licensed under the EPL.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.javaps.gt.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.n52.faroe.Validation;
import org.n52.faroe.annotation.Configurable;
import org.n52.faroe.annotation.Setting;
import org.n52.iceland.service.ServiceSettings;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.geometry.aggregate.MultiCurve;
import org.opengis.geometry.aggregate.MultiSurface;
import org.opengis.geometry.primitive.Curve;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

@Configurable
public class GTHelper {
    private static Logger LOGGER = LoggerFactory.getLogger(GTHelper.class);

    private String serviceURL;

    private static final String GEOMETRY_NAME = "the_geom";

    @Setting(ServiceSettings.SERVICE_URL)
    public void setServiceURL(URI serviceURL) {
        Validation.notNull("serviceURL", serviceURL);
        String url = serviceURL.toString();
        if (url.contains("?")) {
            url = url.split("[?]")[0];
        }
        this.serviceURL = url;
    }

    public SimpleFeatureType createFeatureType(Collection<Property> attributes,
            Geometry newGeometry,
            String uuid,
            CoordinateReferenceSystem coordinateReferenceSystem) {
        String namespace = "http://www.52north.org/" + uuid;

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        if (coordinateReferenceSystem == null) {
            coordinateReferenceSystem = getDefaultCRS();
        }
        typeBuilder.setCRS(coordinateReferenceSystem);
        typeBuilder.setNamespaceURI(namespace);
        Name nameType = new NameImpl(namespace, "Feature-" + uuid);
        typeBuilder.setName(nameType);

        for (Property property : attributes) {
            if (property.getValue() != null) {
                String name = property.getName().getLocalPart();
                Class<?> binding = property.getType().getBinding();
                if (binding.equals(Envelope.class)) {
                    continue;
                }
                if ((binding.equals(Geometry.class) || binding.equals(GeometryCollection.class) || binding.equals(
                        MultiCurve.class) || binding.equals(MultiLineString.class) || binding.equals(Curve.class)
                        || binding.equals(MultiPoint.class) || binding.equals(MultiPolygon.class) || binding.equals(
                                MultiSurface.class) || binding.equals(LineString.class) || binding.equals(Point.class)
                        || binding.equals(LineString.class) || binding.equals(Polygon.class)) && !name.equals(
                                "location")) {

                    if (newGeometry.getClass().equals(Point.class) && (!name.equals("location"))) {
                        typeBuilder.add(GEOMETRY_NAME, MultiPoint.class);
                    } else if (newGeometry.getClass().equals(LineString.class) && (!name.equals("location"))) {

                        typeBuilder.add(GEOMETRY_NAME, MultiLineString.class);
                    } else if (newGeometry.getClass().equals(Polygon.class) && (!name.equals("location"))) {

                        typeBuilder.add(GEOMETRY_NAME, MultiPolygon.class);
                    } else if (!binding.equals(Object.class)) {
                        typeBuilder.add(GEOMETRY_NAME, newGeometry.getClass());
                    }
                } else {
                    if (!name.equals("location") && binding.equals(Object.class)) {
                        try {
                            Geometry g = (Geometry) property.getValue();
                            if (g.getClass().equals(Point.class) && (!name.equals("location"))) {
                                typeBuilder.add(GEOMETRY_NAME, MultiPoint.class);
                            } else if (g.getClass().equals(LineString.class) && (!name.equals("location"))) {

                                typeBuilder.add(GEOMETRY_NAME, MultiLineString.class);
                            } else if (g.getClass().equals(Polygon.class) && (!name.equals("location"))) {

                                typeBuilder.add(GEOMETRY_NAME, MultiPolygon.class);
                            } else {
                                typeBuilder.add(GEOMETRY_NAME, g.getClass());
                            }

                        } catch (ClassCastException e) {

                        }

                    } else if (!name.equals("location")) {
                        typeBuilder.add(name, binding);
                    }
                }
            }

        }

        SimpleFeatureType featureType;

        featureType = typeBuilder.buildFeatureType();
        return featureType;
    }

    public SimpleFeatureType createFeatureType(Geometry newGeometry,
            String uuid,
            CoordinateReferenceSystem coordinateReferenceSystem) {
        String namespace = "http://www.52north.org/" + uuid;

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        if (coordinateReferenceSystem == null) {
            coordinateReferenceSystem = getDefaultCRS();
        }
        typeBuilder.setCRS(coordinateReferenceSystem);
        typeBuilder.setNamespaceURI(namespace);
        Name nameType = new NameImpl(namespace, "Feature-" + uuid);
        typeBuilder.setName(nameType);

        typeBuilder.add(GEOMETRY_NAME, newGeometry.getClass());

        SimpleFeatureType featureType;

        featureType = typeBuilder.buildFeatureType();
        return featureType;
    }

    public SimpleFeature createFeature(String id,
            Geometry geometry,
            SimpleFeatureType featureType,
            Collection<Property> originalAttributes) {

        if (geometry == null || geometry.isEmpty()) {
            return null;
        }

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        SimpleFeature feature = null;
        Collection<PropertyDescriptor> featureTypeAttributes = featureType.getDescriptors();

        Object[] newData = new Object[featureType.getDescriptors().size()];

        int i = 0;
        for (PropertyDescriptor propertyDescriptor : featureTypeAttributes) {
            for (Property originalProperty : originalAttributes) {
                if (propertyDescriptor.getName().getLocalPart().equals(originalProperty.getName().getLocalPart())) {
                    if (propertyDescriptor instanceof GeometryDescriptor) {
                        newData[i] = geometry;
                    } else {
                        newData[i] = originalProperty.getValue();
                    }
                }
            }

            if (propertyDescriptor instanceof GeometryDescriptor) {
                if (geometry.getGeometryType().equals("Point")) {
                    Point[] points = new Point[1];
                    points[0] = (Point) geometry;
                    newData[i] = geometry.getFactory().createMultiPoint(points);
                } else if (geometry.getGeometryType().equals("LineString")) {
                    LineString[] lineString = new LineString[1];
                    lineString[0] = (LineString) geometry;
                    newData[i] = geometry.getFactory().createMultiLineString(lineString);
                } else if (geometry.getGeometryType().equals("Polygon")) {
                    Polygon[] polygons = new Polygon[1];
                    polygons[0] = (Polygon) geometry;
                    newData[i] = geometry.getFactory().createMultiPolygon(polygons);
                } else {
                    newData[i] = geometry;
                }

            }
            i++;
        }

        feature = featureBuilder.buildFeature(id, newData);

        return feature;
    }

    public SimpleFeature createFeature(String id,
            Geometry geometry,
            SimpleFeatureType featureType) {

        if (geometry == null || geometry.isEmpty()) {
            return null;
        }

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        SimpleFeature feature = null;

        Object[] newData = new Object[featureType.getDescriptors().size()];

        int i = 0;

        if (geometry.getGeometryType().equals("Point")) {
            Point[] points = new Point[1];
            points[0] = (Point) geometry;
            newData[i] = geometry.getFactory().createMultiPoint(points);
        } else if (geometry.getGeometryType().equals("LineString")) {
            LineString[] lineString = new LineString[1];
            lineString[0] = (LineString) geometry;
            newData[i] = geometry.getFactory().createMultiLineString(lineString);
        } else if (geometry.getGeometryType().equals("Polygon")) {
            Polygon[] polygons = new Polygon[1];
            polygons[0] = (Polygon) geometry;
            newData[i] = geometry.getFactory().createMultiPolygon(polygons);
        } else {
            newData[i] = geometry;
        }

        feature = featureBuilder.buildFeature(id, newData);

        return feature;
    }

    public QName createGML3SchemaForFeatureType(SimpleFeatureType featureType) {

        String uuid = featureType.getName().getNamespaceURI().replace("http://www.52north.org/", "");
        String namespace = "http://www.52north.org/" + uuid;
        String schema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xs:schema targetNamespace=\"" + namespace + "\" "
                + "xmlns:n52=\"" + namespace + "\" " + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:gml=\"http://www.opengis.net/gml\" " + "elementFormDefault=\"qualified\" "
                + "version=\"1.0\"> " + "<xs:import namespace=\"http://www.opengis.net/gml\" "
                + "schemaLocation=\"http://schemas.opengis.net/gml/3.1.1/base/gml.xsd\"/> ";

        String typeName = featureType.getGeometryDescriptor().getType().getBinding().getName();
        String geometryTypeName = "";
        if (typeName.contains("Point")) {
            geometryTypeName = "PointPropertyType";
        }

        if (typeName.contains("MultiPoint")) {
            geometryTypeName = "MultiPointPropertyType";
        }
        if (typeName.contains("LineString")) {
            geometryTypeName = "CurvePropertyType";
        }
        if (typeName.contains("MultiLineString")) {
            geometryTypeName = "MultiCurvePropertyType";
        }
        if (typeName.contains("Polygon")) {
            geometryTypeName = "SurfacePropertyType";
        }
        if (typeName.contains("MultiPolygon")) {
            geometryTypeName = "MultiSurfacePropertyType";
        }

        // add feature type definition and generic geometry
        schema = schema + "<xs:element name=\"Feature-" + uuid
                + "\" type=\"n52:FeatureType\" substitutionGroup=\"gml:_Feature\"/> "
                + "<xs:complexType name=\"FeatureType\"> " + "<xs:complexContent> "
                + "<xs:extension base=\"gml:AbstractFeatureType\"> " + "<xs:sequence> " +
                // "<xs:element name=\"GEOMETRY\"
                // type=\"gml:GeometryPropertyType\"> "+
                "<xs:element name=\"" + GEOMETRY_NAME + "\" type=\"gml:" + geometryTypeName + "\"> " + "</xs:element> ";

        // add attributes
        Collection<PropertyDescriptor> attributes = featureType.getDescriptors();
        for (PropertyDescriptor property : attributes) {
            String attributeName = property.getName().getLocalPart();
            if (!(property instanceof GeometryDescriptor)) {

                if (property.getType().getBinding().equals(String.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:string\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                } else if (property.getType().getBinding().equals(Integer.class) || property.getType().getBinding()
                        .equals(BigInteger.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:integer\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                } else if (property.getType().getBinding().equals(Double.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:double\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                }
            }
        }

        // close
        schema = schema + "</xs:sequence> " + "</xs:extension> " + "</xs:complexContent> " + "</xs:complexType> "
                + "</xs:schema>";
        String schemalocation = "";
        try {
            schemalocation = storeSchema(schema, uuid);

        } catch (IOException e) {
            LOGGER.error("Exception while storing schema.", e);
            throw new RuntimeException("Exception while storing schema.", e);
        }
        return new QName(namespace, schemalocation);

    }

    public QName createGML2SchemaForFeatureType(SimpleFeatureType featureType) {

        String uuid = featureType.getName().getNamespaceURI().replace("http://www.52north.org/", "");
        String namespace = "http://www.52north.org/" + uuid;
        String schema = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xs:schema targetNamespace=\"" + namespace + "\" "
                + "xmlns:n52=\"" + namespace + "\" " + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:gml=\"http://www.opengis.net/gml\" " + "elementFormDefault=\"qualified\" "
                + "version=\"1.0\"> " + "<xs:import namespace=\"http://www.opengis.net/gml\" "
                + "schemaLocation=\"http://schemas.opengis.net/gml/2.1.2/feature.xsd\"/> ";

        // add feature type definition and generic geometry
        schema = schema + "<xs:element name=\"Feature\" type=\"n52:FeatureType\" substitutionGroup=\"gml:_Feature\"/> "
                + "<xs:complexType name=\"FeatureType\"> " + "<xs:complexContent> "
                + "<xs:extension base=\"gml:AbstractFeatureType\"> " + "<xs:sequence> " + "<xs:element name=\""
                + GEOMETRY_NAME + "\" type=\"gml:GeometryPropertyType\"> " + "</xs:element> ";

        // add attributes
        Collection<PropertyDescriptor> attributes = featureType.getDescriptors();
        for (PropertyDescriptor property : attributes) {
            String attributeName = property.getName().getLocalPart();
            if (!(property instanceof GeometryDescriptor)) {

                if (property.getType().getBinding().equals(String.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:string\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                } else if (property.getType().getBinding().equals(Integer.class) || property.getType().getBinding()
                        .equals(BigInteger.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:integer\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                } else if (property.getType().getBinding().equals(Double.class)) {
                    schema = schema + "<xs:element name=\"" + attributeName + "\" minOccurs=\"0\" maxOccurs=\"1\"> "
                            + "<xs:simpleType> ";
                    schema = schema + "<xs:restriction base=\"xs:double\"> " + "</xs:restriction> "
                            + "</xs:simpleType> " + "</xs:element> ";
                }
            }
        }

        // close
        schema = schema + "</xs:sequence> " + "</xs:extension> " + "</xs:complexContent> " + "</xs:complexType> "
                + "</xs:schema>";
        String schemalocation = "";
        try {
            schemalocation = storeSchema(schema, uuid);

        } catch (IOException e) {
            LOGGER.error("Exception while storing schema.", e);
            throw new RuntimeException("Exception while storing schema.", e);
        }
        return new QName(namespace, schemalocation);

    }

    public String storeSchema(String schema,
            String uuid) throws IOException {

        String domain = GTHelper.class.getProtectionDomain().getCodeSource().getLocation().getFile();

        domain = URLDecoder.decode(domain, "UTF-8");

        int startIndex = domain.indexOf("WEB-INF");
        if (startIndex < 0) {
            // not running as webapp
            File f = File.createTempFile(uuid, ".xsd");
            f.deleteOnExit();
            FileWriter writer = new FileWriter(f);
            writer.write(schema);
            writer.flush();
            writer.close();
            return "file:" + f.getAbsolutePath();
        } else {
            domain = domain.substring(0, startIndex);
            String baseDirLocation = domain;

            String baseDir = baseDirLocation + "static/schemas" + File.separator;
            File folder = new File(baseDir);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File f = new File(baseDir + uuid + ".xsd");
            FileWriter writer = new FileWriter(f);
            writer.write(schema);
            writer.flush();
            writer.close();

            // String url = WPSConfig.getServerBaseURL()+"/schemas/"+
            // uuid+".xsd";
            String url = serviceURL.replace("service", "") + "schemas/" + uuid + ".xsd";
            return url;
        }
    }

    private CoordinateReferenceSystem getDefaultCRS() {

        try {
            return CRS.decode("EPSG:4326");
        } catch (Exception e) {
            LOGGER.error("Exception while decoding CRS EPSG:4326", e);
        }
        return null;
    }

    public SimpleFeatureCollection createSimpleFeatureCollectionFromSimpleFeatureList(List<SimpleFeature> featureList) {

        if (featureList.size() > 0) {
            return new ListFeatureCollection(featureList.get(0).getFeatureType(), featureList);
        }
        return new DefaultFeatureCollection();
    }

}